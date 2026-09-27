package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.SubmissionRequest;
import gdgrvce.iudex.server.dto.SubmissionResponse;
import gdgrvce.iudex.server.dto.SubmissionResult;
import gdgrvce.iudex.server.exception.ContestStateException;
import gdgrvce.iudex.server.exception.ForbiddenOperationException;
import gdgrvce.iudex.server.exception.ResourceNotFoundException;
import gdgrvce.iudex.server.exception.StorageException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.TestCase;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.SubmissionRepository;
import gdgrvce.iudex.server.security.TestCaseCipher;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Grades submissions and serves the attempt history.
 *
 * <p>The client runs the contestant's program against the encrypted test cases
 * it was given and reports what the program printed. This service decrypts
 * those outputs, compares them with the expected ones held in file storage, and
 * derives the passed count. No score is ever taken from the client, and the
 * expected outputs never leave the server.</p>
 *
 * <p>Submissions are immutable once graded, so there is no update path here.
 * They are removed only as collateral when a problem, a contest, or a
 * registration goes.</p>
 */
@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final FileStorageService fileStorageService;
    private final TestCaseCipher cipher;
    private final ContestAccessService access;
    private final SubmissionRateLimiter rateLimiter;

    public SubmissionService(SubmissionRepository submissionRepository,
                             FileStorageService fileStorageService,
                             TestCaseCipher cipher,
                             ContestAccessService access,
                             SubmissionRateLimiter rateLimiter) {
        this.submissionRepository = submissionRepository;
        this.fileStorageService = fileStorageService;
        this.cipher = cipher;
        this.access = access;
        this.rateLimiter = rateLimiter;
    }

    /** Grades one attempt and records it against the server clock. */
    @Transactional
    public SubmissionResponse submit(UserDetails principal, SubmissionRequest request) {
        User user = access.currentUser(principal);
        if (request.contestId() == null || request.problemNum() == null) {
            throw new IllegalArgumentException("contestId and problemNum are required");
        }
        if (request.clientDurationMs() != null && request.clientDurationMs() < 0) {
            throw new IllegalArgumentException("clientDurationMs must not be negative");
        }

        Problem problem = access.requireProblem(request.contestId(), request.problemNum());
        Contest contest = problem.getContest();
        access.requireSubmissionAllowed(contest, user);
        // Only a request the contest would accept spends the caller's slot, so an
        // early or unregistered attempt cannot lock a contestant out once it opens.
        rateLimiter.check(user.getUserId());

        Map<UUID, String> expected = expectedOutputs(contest, problem);
        if (expected.isEmpty()) {
            throw new ContestStateException("This problem has no test cases to grade against");
        }

        Submission submission = new Submission();
        submission.setSubmissionId(UUID.randomUUID());
        submission.setSubmissionNum(nextSubmissionNum(user, problem));
        submission.setUser(user);
        submission.setProblem(problem);
        submission.setPassedTestCaseCount(grade(expected, request.results()));
        // The server clock decides when this arrived; the client does not get a say.
        submission.setReceivedAt(OffsetDateTime.now());
        submission.setClientDurationMs(request.clientDurationMs());

        return toResponse(submissionRepository.save(submission));
    }

    /** Returns one submission to the contestant who made it, or to the contestmaster. */
    @Transactional(readOnly = true)
    public SubmissionResponse get(UUID submissionId, UserDetails principal) {
        User user = access.currentUser(principal);
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("No such submission: " + submissionId));

        Contest contest = submission.getProblem().getContest();
        if (!submission.getUser().getUserId().equals(user.getUserId())
                && !access.owns(contest, user) && !access.isAdmin(user)) {
            throw new ForbiddenOperationException("Only the contestant or the contestmaster may read that");
        }
        return toResponse(submission);
    }

    /**
     * The calling contestant's own attempt history for a problem.
     *
     * <p>Scoped to the caller by construction: there is no parameter that could
     * point this at somebody else's attempts.</p>
     */
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listMine(UUID contestId, int problemNum, UserDetails principal) {
        User user = access.currentUser(principal);
        Problem problem = access.requireProblem(contestId, problemNum);
        ProblemId problemId = problem.getProblemId();

        return submissionRepository.findByProblemAndUser(
                        problemId.getContestId(), problemId.getProblemNum(), user.getUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Every submission in a contest. Contestmaster and administrators only. */
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listForContest(UUID contestId, UserDetails principal) {
        User user = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);
        access.requireContestOwner(contest, user);

        return submissionRepository.findByContestId(contestId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Counts the test cases whose reported output matches the expected one.
     *
     * <p>An output for a test case the problem does not have is ignored rather
     * than rejected, so replacing the test data under an in-flight submission
     * does not fail it outright. A repeated test case id counts once: the first
     * answer stands, so a client cannot improve its score by sending a case
     * twice.</p>
     */
    private int grade(Map<UUID, String> expected, List<SubmissionResult> results) {
        if (results == null || results.isEmpty()) {
            return 0;
        }

        Set<UUID> seen = new HashSet<>();
        int passed = 0;
        for (SubmissionResult result : results) {
            if (result == null || result.testCaseId() == null) {
                throw new IllegalArgumentException("every result needs a testCaseId");
            }
            if (result.encryptedOutput() == null) {
                throw new IllegalArgumentException("every result needs an encryptedOutput");
            }
            String want = expected.get(result.testCaseId());
            if (want == null || !seen.add(result.testCaseId())) {
                continue;
            }
            if (OutputComparator.matches(cipher.decrypt(result.encryptedOutput()), want)) {
                passed++;
            }
        }
        return passed;
    }

    /** Both collections count towards the score; only the client can tell them apart. */
    private Map<UUID, String> expectedOutputs(Contest contest, Problem problem) {
        Map<UUID, String> expected = new HashMap<>();
        try {
            for (TestCase testCase : fileStorageService.readSampleTestCases(contest, problem)) {
                expected.put(testCase.id(), testCase.output());
            }
            for (TestCase testCase : fileStorageService.readHiddenTestCases(contest, problem)) {
                expected.put(testCase.id(), testCase.output());
            }
        } catch (IOException exception) {
            throw new StorageException("Unable to read test cases", exception);
        }
        return expected;
    }

    private int nextSubmissionNum(User user, Problem problem) {
        ProblemId problemId = problem.getProblemId();
        return submissionRepository.findHighestSubmissionNum(
                problemId.getContestId(), problemId.getProblemNum(), user.getUserId()) + 1;
    }

    private SubmissionResponse toResponse(Submission submission) {
        Problem problem = submission.getProblem();
        return new SubmissionResponse(
                submission.getSubmissionId(),
                problem.getProblemId().getContestId(),
                problem.getProblemId().getProblemNum(),
                submission.getSubmissionNum(),
                submission.getUser().getUserId(),
                submission.getUser().getUsername(),
                submission.getPassedTestCaseCount(),
                problem.getTestCaseCount(),
                submission.getReceivedAt(),
                submission.getClientDurationMs());
    }
}

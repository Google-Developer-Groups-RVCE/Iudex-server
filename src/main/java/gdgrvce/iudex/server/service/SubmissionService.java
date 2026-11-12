package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.SubmissionRequest;
import gdgrvce.iudex.server.dto.SubmissionResponse;
import gdgrvce.iudex.server.dto.SubmissionSource;
import gdgrvce.iudex.server.exception.ContestStateException;
import gdgrvce.iudex.server.exception.ForbiddenOperationException;
import gdgrvce.iudex.server.exception.ResourceNotFoundException;
import gdgrvce.iudex.server.exception.StorageException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Language;
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
import java.util.List;
import java.util.UUID;

/**
 * Grades submissions and serves the attempt history.
 *
 * <p>The client runs the contestant's program against the encrypted test cases
 * it was given and reports what the program printed. This service decrypts
 * those outputs, compares them with the expected ones held in file storage, and
 * derives the verdict and passed count (see {@link Grader}). No score or
 * verdict is ever taken from the client, and the expected outputs never leave
 * the server.</p>
 *
 * <p>The source code travels with every attempt, but only a contestant's latest
 * attempt at a problem keeps it.</p>
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
    private final SourceCodeLimit sourceCodeLimit;

    public SubmissionService(SubmissionRepository submissionRepository,
                             FileStorageService fileStorageService,
                             TestCaseCipher cipher,
                             ContestAccessService access,
                             SubmissionRateLimiter rateLimiter,
                             SourceCodeLimit sourceCodeLimit) {
        this.submissionRepository = submissionRepository;
        this.fileStorageService = fileStorageService;
        this.cipher = cipher;
        this.access = access;
        this.rateLimiter = rateLimiter;
        this.sourceCodeLimit = sourceCodeLimit;
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
        Language language = Language.fromId(request.language());
        sourceCodeLimit.check("sourceCode", request.sourceCode());

        Problem problem = access.requireProblem(request.contestId(), request.problemNum());
        Contest contest = problem.getContest();
        access.requireSubmissionAllowed(contest, user);
        // Only a request the contest would accept spends the caller's slot, so an
        // early or unregistered attempt cannot lock a contestant out once it opens.
        rateLimiter.check(user.getUserId());

        List<TestCase> samples;
        List<TestCase> hidden;
        try {
            samples = fileStorageService.readSampleTestCases(contest, problem);
            hidden = fileStorageService.readHiddenTestCases(contest, problem);
        } catch (IOException exception) {
            throw new StorageException("Unable to read test cases", exception);
        }
        if (samples.isEmpty() && hidden.isEmpty()) {
            throw new ContestStateException("This problem has no test cases to grade against");
        }

        Grader.Grade grade = Grader.grade(samples, hidden, request.compilation(), request.results(),
                cipher::decrypt);

        Submission submission = new Submission();
        submission.setSubmissionId(UUID.randomUUID());
        submission.setSubmissionNum(nextSubmissionNum(user, problem));
        submission.setUser(user);
        submission.setProblem(problem);
        submission.setVerdict(grade.verdict());
        submission.setMessage(grade.message());
        submission.setPassedTestCaseCount(grade.passed());
        submission.setCompilerOutput(grade.compilerOutput());
        submission.setLanguage(language.id());
        submission.setSourceCode(request.sourceCode());
        // The server clock decides when this arrived; the client does not get a say.
        submission.setReceivedAt(OffsetDateTime.now());
        submission.setClientDurationMs(request.clientDurationMs());

        Submission saved = submissionRepository.saveAndFlush(submission);
        ProblemId problemId = problem.getProblemId();
        submissionRepository.clearSourceOfEarlierAttempts(problemId.getContestId(), problemId.getProblemNum(),
                user.getUserId(), saved.getSubmissionId());
        return toResponse(saved);
    }

    /**
     * The source code of a submission, for its author, the contestmaster, or an
     * administrator. Only a contestant's latest attempt at a problem keeps it.
     */
    @Transactional(readOnly = true)
    public SubmissionSource source(UUID submissionId, UserDetails principal) {
        Submission submission = readable(submissionId, principal);
        if (submission.getSourceCode() == null) {
            throw new ResourceNotFoundException(
                    "Source code is kept only for the latest attempt at a problem");
        }
        return new SubmissionSource(submission.getSubmissionId(), submission.getLanguage(),
                submission.getSourceCode());
    }

    /** Returns one submission to the contestant who made it, or to the contestmaster. */
    @Transactional(readOnly = true)
    public SubmissionResponse get(UUID submissionId, UserDetails principal) {
        return toResponse(readable(submissionId, principal));
    }

    private Submission readable(UUID submissionId, UserDetails principal) {
        User user = access.currentUser(principal);
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("No such submission: " + submissionId));

        Contest contest = submission.getProblem().getContest();
        if (!submission.getUser().getUserId().equals(user.getUserId())
                && !access.owns(contest, user) && !access.isAdmin(user)) {
            throw new ForbiddenOperationException("Only the contestant or the contestmaster may read that");
        }
        return submission;
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
                submission.getLanguage(),
                submission.getVerdict(),
                submission.getMessage(),
                submission.getPassedTestCaseCount(),
                problem.getTestCaseCount(),
                submission.getReceivedAt(),
                submission.getClientDurationMs(),
                submission.getCompilerOutput());
    }
}

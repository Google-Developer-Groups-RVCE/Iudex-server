package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.SubmitRequest;
import gdgrvce.iudex.server.dto.SubmitResponse;
import gdgrvce.iudex.server.exception.InvalidSubmissionException;
import gdgrvce.iudex.server.exception.ProblemNotFoundException;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.SubmissionId;
import gdgrvce.iudex.server.model.TestCase;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.ProblemRepository;
import gdgrvce.iudex.server.repository.SubmissionRepository;
import gdgrvce.iudex.server.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

/** Accepts a user's outputs, judges them against the hidden testcases, and records the result. */
@Service
public class SubmissionService {

    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final FileStorageService fileStorageService;
    private final SubmissionRateLimiter rateLimiter;

    public SubmissionService(
            UserRepository userRepository,
            ProblemRepository problemRepository,
            SubmissionRepository submissionRepository,
            FileStorageService fileStorageService,
            SubmissionRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.fileStorageService = fileStorageService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public SubmitResponse judge(String username, UUID contestId, int problemNum, SubmitRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));

        rateLimiter.check(user.getUserId());

        ProblemId problemId = problemId(contestId, problemNum);
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(
                        "Problem " + problemNum + " not found for contest " + contestId));

        List<TestCase> hiddenTestCases = readHiddenTestCases(problem);

        List<String> outputs = request == null ? null : request.outputs();
        if (outputs == null || outputs.size() != hiddenTestCases.size()) {
            throw new InvalidSubmissionException(
                    "Expected " + hiddenTestCases.size() + " outputs but received "
                            + (outputs == null ? 0 : outputs.size()));
        }

        int passed = countPassed(outputs, hiddenTestCases);
        int submissionNum = persist(user, problem, passed);

        int total = hiddenTestCases.size();
        String verdict = passed == total ? "PASS" : "FAIL";
        return new SubmitResponse(submissionNum, passed, total, verdict);
    }

    private int countPassed(List<String> outputs, List<TestCase> hiddenTestCases) {
        int passed = 0;
        for (int i = 0; i < hiddenTestCases.size(); i++) {
            if (OutputComparator.matches(outputs.get(i), hiddenTestCases.get(i).output())) {
                passed++;
            }
        }
        return passed;
    }

    private int persist(User user, Problem problem, int passed) {
        int submissionNum = (int) submissionRepository
                .countBySubmissionIdUserIdAndSubmissionIdProblemId(user.getUserId(), problem.getProblemId()) + 1;

        SubmissionId submissionId = new SubmissionId();
        submissionId.setUserId(user.getUserId());
        submissionId.setProblemId(problem.getProblemId());
        submissionId.setSubmissionNum(submissionNum);

        Submission submission = new Submission();
        submission.setSubmissionId(submissionId);
        submission.setUser(user);
        submission.setProblem(problem);
        submission.setPassedTestCaseCount(passed);

        submissionRepository.save(submission);
        return submissionNum;
    }

    private List<TestCase> readHiddenTestCases(Problem problem) {
        try {
            return fileStorageService.readHiddenTestCases(problem.getContest(), problem);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read hidden testcases", exception);
        }
    }

    private ProblemId problemId(UUID contestId, int problemNum) {
        ProblemId problemId = new ProblemId();
        problemId.setContestId(contestId);
        problemId.setProblemNum(problemNum);
        return problemId;
    }
}

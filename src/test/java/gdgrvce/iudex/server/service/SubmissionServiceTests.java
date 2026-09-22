package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.SubmitRequest;
import gdgrvce.iudex.server.dto.SubmitResponse;
import gdgrvce.iudex.server.exception.InvalidSubmissionException;
import gdgrvce.iudex.server.exception.ProblemNotFoundException;
import gdgrvce.iudex.server.exception.RateLimitExceededException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.Role;
import gdgrvce.iudex.server.model.Submission;
import gdgrvce.iudex.server.model.TestCase;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.ProblemRepository;
import gdgrvce.iudex.server.repository.SubmissionRepository;
import gdgrvce.iudex.server.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTests {

    private static final String USERNAME = "solver";
    private static final UUID CONTEST_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private SubmissionRateLimiter rateLimiter;

    @InjectMocks
    private SubmissionService service;

    @Test
    void allCorrectOutputsPassAndPersistTheCount() throws Exception {
        User user = user();
        Problem problem = problem();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(problemRepository.findById(any(ProblemId.class))).thenReturn(Optional.of(problem));
        when(fileStorageService.readHiddenTestCases(any(), any()))
                .thenReturn(List.of(new TestCase("1 2", "3"), new TestCase("4 5", "9")));

        SubmitResponse response = service.judge(USERNAME, CONTEST_ID, 1, new SubmitRequest(List.of("3", "9")));

        assertEquals(1, response.submissionNum());
        assertEquals(2, response.passedTestCaseCount());
        assertEquals(2, response.totalTestCaseCount());
        assertEquals("PASS", response.verdict());

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        assertEquals(2, saved.getValue().getPassedTestCaseCount());
        assertEquals(1, saved.getValue().getSubmissionId().getSubmissionNum());
    }

    @Test
    void submissionNumberFollowsExistingCount() throws Exception {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(problemRepository.findById(any(ProblemId.class))).thenReturn(Optional.of(problem()));
        when(fileStorageService.readHiddenTestCases(any(), any()))
                .thenReturn(List.of(new TestCase("1 2", "3")));
        when(submissionRepository.countBySubmissionIdUserIdAndSubmissionIdProblemId(any(), any()))
                .thenReturn(4L);

        SubmitResponse response = service.judge(USERNAME, CONTEST_ID, 1, new SubmitRequest(List.of("3")));

        assertEquals(5, response.submissionNum());
    }

    @Test
    void partiallyCorrectOutputsFail() throws Exception {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(problemRepository.findById(any(ProblemId.class))).thenReturn(Optional.of(problem()));
        when(fileStorageService.readHiddenTestCases(any(), any()))
                .thenReturn(List.of(new TestCase("1 2", "3"), new TestCase("4 5", "9")));

        SubmitResponse response = service.judge(USERNAME, CONTEST_ID, 1, new SubmitRequest(List.of("3", "10")));

        assertEquals(1, response.passedTestCaseCount());
        assertEquals("FAIL", response.verdict());
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.judge(USERNAME, CONTEST_ID, 1, new SubmitRequest(List.of("3"))));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void missingProblemIsRejectedWithoutPersisting() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(problemRepository.findById(any(ProblemId.class))).thenReturn(Optional.empty());

        assertThrows(ProblemNotFoundException.class,
                () -> service.judge(USERNAME, CONTEST_ID, 1, new SubmitRequest(List.of("3"))));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void wrongOutputCountIsRejectedWithoutPersisting() throws Exception {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user()));
        when(problemRepository.findById(any(ProblemId.class))).thenReturn(Optional.of(problem()));
        when(fileStorageService.readHiddenTestCases(any(), any()))
                .thenReturn(List.of(new TestCase("1 2", "3"), new TestCase("4 5", "9")));

        assertThrows(InvalidSubmissionException.class,
                () -> service.judge(USERNAME, CONTEST_ID, 1, new SubmitRequest(List.of("3"))));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void rateLimitIsCheckedBeforeJudging() {
        User user = user();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        doThrow(new RateLimitExceededException("slow down", 2)).when(rateLimiter).check(user.getUserId());

        assertThrows(RateLimitExceededException.class,
                () -> service.judge(USERNAME, CONTEST_ID, 1, new SubmitRequest(List.of("3"))));
        verify(problemRepository, never()).findById(any(ProblemId.class));
        verify(submissionRepository, never()).save(any());
    }

    private User user() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername(USERNAME);
        user.setPasswordHash("hash");
        user.setRole(Role.PARTICIPANT);
        return user;
    }

    private Problem problem() {
        Contest contest = new Contest();
        contest.setContestId(CONTEST_ID);

        ProblemId problemId = new ProblemId();
        problemId.setContestId(CONTEST_ID);
        problemId.setProblemNum(1);

        Problem problem = new Problem();
        problem.setProblemId(problemId);
        problem.setContest(contest);
        problem.setTestCaseCount(2);
        return problem;
    }
}

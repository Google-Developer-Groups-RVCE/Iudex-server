package gdgrvce.iudex.server.model;

import gdgrvce.iudex.server.repository.ContestRepository;
import gdgrvce.iudex.server.repository.ProblemRepository;
import gdgrvce.iudex.server.repository.RegistrationRepository;
import gdgrvce.iudex.server.repository.SubmissionRepository;
import gdgrvce.iudex.server.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every entity against the real Flyway migration.
 *
 * <p>The context runs against in-memory H2 with validate-only schema
 * handling, so a mapping that disagrees with the migration fails here at
 * startup rather than at the first write in production.</p>
 */
@SpringBootTest
@Transactional
class PersistenceMappingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsTheWholeContestGraph() {
        User contestmaster = saveUser("cm_graph", Role.CONTESTMASTER);
        User contestant = saveUser("contestant_graph", Role.CONTESTANT);
        Contest contest = saveContest(contestmaster, "Graph Cup");
        Problem problem = saveProblem(contest, 1, 5);
        saveRegistration(contest, contestant);
        Submission submission = saveSubmission(contestant, problem, 1, 5);

        reload();

        Problem loadedProblem = problemRepository.findById(problem.getProblemId()).orElseThrow();
        assertEquals(contest.getContestId(), loadedProblem.getContest().getContestId());
        assertEquals(1, loadedProblem.getProblemId().getProblemNum());
        assertEquals(5, loadedProblem.getTestCaseCount());

        Submission loadedSubmission = submissionRepository.findById(submission.getSubmissionId()).orElseThrow();
        assertEquals(contestant.getUserId(), loadedSubmission.getUser().getUserId());
        assertEquals(1, loadedSubmission.getProblem().getProblemId().getProblemNum());
        assertEquals(contest.getContestId(), loadedSubmission.getProblem().getContest().getContestId());
        assertEquals(5, loadedSubmission.getPassedTestCaseCount());

        RegistrationId registrationId = new RegistrationId();
        registrationId.setContestId(contest.getContestId());
        registrationId.setUserId(contestant.getUserId());
        assertTrue(registrationRepository.findById(registrationId).isPresent());
    }

    @Test
    void storesEveryRole() {
        User admin = saveUser("role_admin", Role.ADMIN);
        User contestmaster = saveUser("role_cm", Role.CONTESTMASTER);
        User contestant = saveUser("role_contestant", Role.CONTESTANT);

        reload();

        assertEquals(Role.ADMIN, userRepository.findById(admin.getUserId()).orElseThrow().getRole());
        assertEquals(Role.CONTESTMASTER, userRepository.findById(contestmaster.getUserId()).orElseThrow().getRole());
        assertEquals(Role.CONTESTANT, userRepository.findById(contestant.getUserId()).orElseThrow().getRole());
    }

    @Test
    void contestWindowRoundTripsAsAnInstantNotALocalTime() {
        User contestmaster = saveUser("cm_tz", Role.CONTESTMASTER);
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30))
                .truncatedTo(ChronoUnit.MICROS);

        Contest contest = new Contest();
        contest.setContestName("Timezone Cup");
        contest.setHostId(contestmaster.getUserId());
        contest.setStartTime(start);
        contest.setEndTime(start.plusHours(3));
        contest = contestRepository.save(contest);

        reload();

        Contest loaded = contestRepository.findById(contest.getContestId()).orElseThrow();
        assertEquals(start.toInstant(), loaded.getStartTime().toInstant());
        assertEquals(start.plusHours(3).toInstant(), loaded.getEndTime().toInstant());
    }

    @Test
    void submissionSeparatesServerReceiptTimeFromClientTelemetry() {
        User contestant = saveUser("contestant_telemetry", Role.CONTESTANT);
        Contest contest = saveContest(saveUser("cm_telemetry", Role.CONTESTMASTER), "Telemetry Cup");
        Problem problem = saveProblem(contest, 1, 3);

        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        Submission submission = newSubmission(contestant, problem, 1, 2);
        submission.setReceivedAt(receivedAt);
        submission.setClientDurationMs(42);
        submissionRepository.save(submission);

        reload();

        Submission loaded = submissionRepository.findById(submission.getSubmissionId()).orElseThrow();
        assertEquals(receivedAt.toInstant(), loaded.getReceivedAt().toInstant());
        assertEquals(42, loaded.getClientDurationMs());
    }

    @Test
    void clientDurationIsOptionalButReceiptTimeIsNot() {
        User contestant = saveUser("contestant_optional", Role.CONTESTANT);
        Contest contest = saveContest(saveUser("cm_optional", Role.CONTESTMASTER), "Optional Cup");
        Problem problem = saveProblem(contest, 1, 3);

        Submission submission = newSubmission(contestant, problem, 1, 0);
        submission.setClientDurationMs(null);
        submissionRepository.save(submission);

        reload();

        assertNull(submissionRepository.findById(submission.getSubmissionId()).orElseThrow()
                .getClientDurationMs());
    }

    @Test
    void flatProblemAndSubmissionIdentifiersArePresent() {
        User contestant = saveUser("contestant_flat", Role.CONTESTANT);
        Contest contest = saveContest(saveUser("cm_flat", Role.CONTESTMASTER), "Flat Cup");
        Problem problem = saveProblem(contest, 1, 1);
        Submission submission = saveSubmission(contestant, problem, 1, 1);

        reload();

        assertNotNull(problemRepository.findById(problem.getProblemId()).orElseThrow().getProblemUuid());
        assertNotNull(submissionRepository.findById(submission.getSubmissionId()).orElseThrow()
                .getSubmissionUuid());
    }

    @Test
    void rejectsTwoProblemsSharingAFlatIdentifier() {
        Contest contest = saveContest(saveUser("cm_dupe", Role.CONTESTMASTER), "Duplicate Cup");
        Problem first = saveProblem(contest, 1, 1);

        Problem second = newProblem(contest, 2, 1);
        second.setProblemUuid(first.getProblemUuid());

        // Flush through the repository: a bare EntityManager.flush() bypasses
        // Spring's persistence exception translation.
        assertThrows(DataIntegrityViolationException.class,
                () -> problemRepository.saveAndFlush(second));
    }

    @Test
    void keepsSubmissionHistoryPerProblem() {
        User contestant = saveUser("contestant_history", Role.CONTESTANT);
        Contest contest = saveContest(saveUser("cm_history", Role.CONTESTMASTER), "History Cup");
        Problem problem = saveProblem(contest, 1, 4);

        saveSubmission(contestant, problem, 1, 1);
        saveSubmission(contestant, problem, 2, 3);
        saveSubmission(contestant, problem, 3, 4);

        reload();

        assertEquals(3, submissionRepository.findAll().stream()
                .filter(each -> each.getUser().getUserId().equals(contestant.getUserId()))
                .count());
    }

    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private User saveUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("hash");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Contest saveContest(User contestmaster, String name) {
        Contest contest = new Contest();
        contest.setContestName(name);
        contest.setHostId(contestmaster.getUserId());
        contest.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        contest.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).truncatedTo(ChronoUnit.MICROS));
        return contestRepository.save(contest);
    }

    private Problem newProblem(Contest contest, int problemNumber, int testCaseCount) {
        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setContestId(contest.getContestId());
        problemId.setProblemNum(problemNumber);
        problem.setProblemId(problemId);
        problem.setProblemUuid(UUID.randomUUID());
        problem.setContest(contest);
        problem.setTestCaseCount(testCaseCount);
        return problem;
    }

    private Problem saveProblem(Contest contest, int problemNumber, int testCaseCount) {
        return problemRepository.save(newProblem(contest, problemNumber, testCaseCount));
    }

    private void saveRegistration(Contest contest, User user) {
        Registration registration = new Registration();
        RegistrationId registrationId = new RegistrationId();
        registrationId.setContestId(contest.getContestId());
        registrationId.setUserId(user.getUserId());
        registration.setRegId(registrationId);
        registration.setContest(contest);
        registration.setUser(user);
        registration.setRegistrationTime(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        registrationRepository.save(registration);
    }

    private Submission newSubmission(User user, Problem problem, int submissionNumber, int passed) {
        Submission submission = new Submission();
        SubmissionId submissionId = new SubmissionId();
        submissionId.setUserId(user.getUserId());
        submissionId.setProblemId(problem.getProblemId());
        submissionId.setSubmissionNum(submissionNumber);
        submission.setSubmissionId(submissionId);
        submission.setSubmissionUuid(UUID.randomUUID());
        submission.setUser(user);
        submission.setProblem(problem);
        submission.setPassedTestCaseCount(passed);
        submission.setReceivedAt(OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        submission.setClientDurationMs(10);
        return submission;
    }

    private Submission saveSubmission(User user, Problem problem, int submissionNumber, int passed) {
        return submissionRepository.save(newSubmission(user, problem, submissionNumber, passed));
    }
}

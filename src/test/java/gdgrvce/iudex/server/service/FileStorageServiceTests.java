package gdgrvce.iudex.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsContestAndProblemFiles() throws Exception {
        FileStorageService service = service();
        Contest contest = contest();
        Problem problem = problem(contest, 1);

        service.createContest(contest);
        service.createProblemFiles(contest, problem, "Add two numbers");

        Path problemDirectory = temporaryDirectory.resolve(contest.getContestId().toString()).resolve("1");
        assertTrue(service.contestExists(contest));
        assertTrue(service.problemExists(contest, problem));
        assertEquals("Add two numbers", Files.readString(problemDirectory.resolve("statement.txt")));
        assertEquals("", Files.readString(problemDirectory.resolve("template.txt")));
        assertEquals(List.of(), service.readSampleTestCases(contest, problem));
        assertEquals(List.of(), service.readHiddenTestCases(contest, problem));
    }

    @Test
    void rejectsDuplicateContestAndProblemCreation() throws Exception {
        FileStorageService service = service();
        Contest contest = contest();
        Problem problem = problem(contest, 1);

        service.createContest(contest);
        assertThrows(IllegalStateException.class, () -> service.createContest(contest));

        service.createProblemFiles(contest, problem, "statement");
        assertThrows(IllegalStateException.class,
                () -> service.createProblemFiles(contest, problem, "replacement"));
    }

    @Test
    void removesTemporaryProblemDirectoryWhenCreationFails() throws Exception {
        FileStorageService service = service();
        Contest contest = contest();
        Problem problem = problem(contest, 1);

        service.createContest(contest);

        assertThrows(NullPointerException.class,
                () -> service.createProblemFiles(contest, problem, null));

        Path contestDirectory = temporaryDirectory.resolve(contest.getContestId().toString());
        assertFalse(Files.exists(contestDirectory.resolve("1")));
        assertFalse(Files.exists(contestDirectory.resolve("temp_1")));
    }

    @Test
    void requiresExistingContestAndProblemForWrites() {
        FileStorageService service = service();
        Contest contest = contest();
        Problem problem = problem(contest, 1);

        assertThrows(IllegalStateException.class,
                () -> service.createProblemFiles(contest, problem, "statement"));
        assertThrows(IllegalStateException.class,
                () -> service.saveStatement(contest, problem, "statement"));
    }

    @Test
    void storesAndReadsBothTestcaseCollections() throws Exception {
        FileStorageService service = service();
        Contest contest = contest();
        Problem problem = problem(contest, 1);
        List<TestCase> sampleTestCases = List.of(new TestCase("1 2", "3"));
        List<TestCase> hiddenTestCases = List.of(new TestCase("10 20", "30"));

        service.createContest(contest);
        service.createProblemFiles(contest, problem, "statement");
        service.saveSampleTestCases(contest, problem, sampleTestCases);
        service.saveHiddenTestCases(contest, problem, hiddenTestCases);

        assertEquals(sampleTestCases, service.readSampleTestCases(contest, problem));
        assertEquals(hiddenTestCases, service.readHiddenTestCases(contest, problem));
    }

    @Test
    void deletesProblemAndContestOnlyWhenTheyExist() throws Exception {
        FileStorageService service = service();
        Contest contest = contest();
        Problem problem = problem(contest, 1);

        assertThrows(IllegalStateException.class, () -> service.deleteContest(contest));
        service.createContest(contest);
        service.createProblemFiles(contest, problem, "statement");

        service.deleteProblem(contest, problem);
        assertTrue(service.contestExists(contest));
        assertFalse(service.problemExists(contest, problem));
        assertThrows(IllegalStateException.class, () -> service.deleteProblem(contest, problem));

        service.deleteContest(contest);
        assertFalse(service.contestExists(contest));
        assertThrows(IllegalStateException.class, () -> service.deleteContest(contest));
    }

    private FileStorageService service() {
        return new FileStorageService(temporaryDirectory, new ObjectMapper());
    }

    private Contest contest() {
        Contest contest = new Contest();
        contest.setContestId(UUID.randomUUID());
        return contest;
    }

    private Problem problem(Contest contest, int problemNumber) {
        Problem problem = new Problem();
        ProblemId problemId = new ProblemId();
        problemId.setContestId(contest.getContestId());
        problemId.setProblemNum(problemNumber);
        problem.setProblemId(problemId);
        problem.setContest(contest);
        return problem;
    }
}
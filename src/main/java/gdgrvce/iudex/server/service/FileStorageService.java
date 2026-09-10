package gdgrvce.iudex.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


/**
 * Stores contest problem content on the local filesystem.
 *
 * <p>The database remains the source of truth for contest and problem
 * metadata. This service stores only the statement, template solution, and
 * sample or hidden test cases.</p>
 */
@Service
public class FileStorageService {

    private static final String STATEMENT_FILE = "statement.txt";
    private static final String TEMPLATE_FILE = "template.txt";
    private static final String SAMPLE_TEST_CASES_FILE = "sample_testcases.json";
    private static final String HIDDEN_TEST_CASES_FILE = "hidden_testcases.json";

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    @Autowired
    public FileStorageService(
            @Value("${iudex.storage.root:./storage}") String storageRoot,
            ObjectMapper objectMapper) {
        this(Path.of(storageRoot), objectMapper);
    }

    public FileStorageService(Path storageRoot, ObjectMapper objectMapper) {
        this.storageRoot = Objects.requireNonNull(storageRoot, "storageRoot must not be null")
                .toAbsolutePath()
                .normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /** Returns whether the contest has a directory in file storage. */
    public boolean contestExists(Contest contest) {
        UUID contestId = contestId(contest);
        return Files.isDirectory(contestDirectory(contestId));
    }

    /** Returns whether the problem has a directory in file storage. */
    public boolean problemExists(Contest contest, Problem problem) {
        return Files.isDirectory(problemDirectory(contest, problem));
    }

    /**
     * Creates a problem directory and its initial files. The template starts
     * empty, and both testcase files start as empty JSON arrays.
     */
    public void createProblemFiles(Contest contest, Problem problem, String statement) throws IOException {
        Path problemDirectory = problemDirectory(contest, problem);
        Files.createDirectories(problemDirectory);

        Files.writeString(problemDirectory.resolve(STATEMENT_FILE), statement, StandardCharsets.UTF_8);
        Files.writeString(problemDirectory.resolve(TEMPLATE_FILE), "", StandardCharsets.UTF_8);
        writeTestCases(problemDirectory.resolve(SAMPLE_TEST_CASES_FILE), List.of());
        writeTestCases(problemDirectory.resolve(HIDDEN_TEST_CASES_FILE), List.of());
    }

    /** Replaces the problem statement. */
    public void saveStatement(Contest contest, Problem problem, String statement) throws IOException {
        Files.writeString(problemDirectory(contest, problem).resolve(STATEMENT_FILE), statement,
                StandardCharsets.UTF_8);
    }

    /** Replaces the template solution. */
    public void saveTemplateSolution(Contest contest, Problem problem, String template) throws IOException {
        Files.writeString(problemDirectory(contest, problem).resolve(TEMPLATE_FILE), template,
                StandardCharsets.UTF_8);
    }

    /** Replaces the sample testcase collection. */
    public void saveSampleTestCases(Contest contest, Problem problem, List<TestCase> testCases)
            throws IOException {
        saveTestCases(contest, problem, SAMPLE_TEST_CASES_FILE, testCases);
    }

    /** Replaces the hidden testcase collection. */
    public void saveHiddenTestCases(Contest contest, Problem problem, List<TestCase> testCases)
            throws IOException {
        saveTestCases(contest, problem, HIDDEN_TEST_CASES_FILE, testCases);
    }

    /** Reads the problem statement. */
    public String readStatement(Contest contest, Problem problem) throws IOException {
        return Files.readString(problemDirectory(contest, problem).resolve(STATEMENT_FILE), StandardCharsets.UTF_8);
    }

    /** Reads the template solution. */
    public String readTemplateSolution(Contest contest, Problem problem) throws IOException {
        return Files.readString(problemDirectory(contest, problem).resolve(TEMPLATE_FILE), StandardCharsets.UTF_8);
    }

    /** Reads the sample testcase collection. */
    public List<TestCase> readSampleTestCases(Contest contest, Problem problem) throws IOException {
        return readTestCases(contest, problem, SAMPLE_TEST_CASES_FILE);
    }

    /** Reads the hidden testcase collection. */
    public List<TestCase> readHiddenTestCases(Contest contest, Problem problem) throws IOException {
        return readTestCases(contest, problem, HIDDEN_TEST_CASES_FILE);
    }

    /** Deletes all stored files for a problem. */
    public void deleteProblem(Contest contest, Problem problem) throws IOException {
        Path problemDirectory = problemDirectory(contest, problem);
        if (Files.notExists(problemDirectory)) {
            return;
        }
        try (var paths = Files.walk(problemDirectory)) {
            paths.sorted((first, second) -> second.compareTo(first)).forEach(path -> delete(path));
        }
    }

    private void saveTestCases(Contest contest, Problem problem, String fileName, List<TestCase> testCases)
            throws IOException {
        Objects.requireNonNull(testCases, "testCases must not be null");
        Path problemDirectory = problemDirectory(contest, problem);
        Files.createDirectories(problemDirectory);
        writeTestCases(problemDirectory.resolve(fileName), testCases);
    }

    private List<TestCase> readTestCases(Contest contest, Problem problem, String fileName) throws IOException {
        return objectMapper.readValue(
                Files.readString(problemDirectory(contest, problem).resolve(fileName), StandardCharsets.UTF_8),
                new TypeReference<>() {
                });
    }

    private void writeTestCases(Path file, List<TestCase> testCases) throws IOException {
        Files.writeString(file, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(testCases),
                StandardCharsets.UTF_8);
    }

    private Path problemDirectory(Contest contest, Problem problem) {
        Objects.requireNonNull(problem, "problem must not be null");

        UUID contestId = contestId(contest);
        ProblemId problemId = Objects.requireNonNull(problem.getProblemId(), "problem ID must not be null");
        if (!contestId.equals(problemId.getContestId())) {
            throw new IllegalArgumentException("problem does not belong to the supplied contest");
        }
        if (problemId.getProblemNum() < 1) {
            throw new IllegalArgumentException("problem number must be positive");
        }

        return contestDirectory(contestId)
                .resolve(Integer.toString(problemId.getProblemNum()))
                .normalize();
    }

    private UUID contestId(Contest contest) {
        Objects.requireNonNull(contest, "contest must not be null");
        return Objects.requireNonNull(contest.getContestId(), "contest ID must not be null");
    }

    private Path contestDirectory(UUID contestId) {
        return storageRoot.resolve(contestId.toString()).normalize();
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new FileStorageException("Unable to delete " + path, exception);
        }
    }

    private static class FileStorageException extends RuntimeException {
        private FileStorageException(String message, IOException cause) {
            super(message, cause);
        }
    }
}
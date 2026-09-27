package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.ProblemRequest;
import gdgrvce.iudex.server.dto.ProblemResponse;
import gdgrvce.iudex.server.dto.ProblemSummary;
import gdgrvce.iudex.server.dto.TestCaseData;
import gdgrvce.iudex.server.dto.EncryptedTestCase;
import gdgrvce.iudex.server.dto.TestCaseUploadRequest;
import gdgrvce.iudex.server.exception.StorageException;
import gdgrvce.iudex.server.model.Contest;
import gdgrvce.iudex.server.model.Problem;
import gdgrvce.iudex.server.model.ProblemId;
import gdgrvce.iudex.server.model.ProblemMetadata;
import gdgrvce.iudex.server.model.TestCase;
import gdgrvce.iudex.server.model.User;
import gdgrvce.iudex.server.repository.ProblemRepository;
import gdgrvce.iudex.server.repository.SubmissionRepository;
import gdgrvce.iudex.server.security.TestCaseCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Create, read, update, and delete for problems and their test data.
 *
 * <p>The database holds only a problem's identity and ordering. Title, limits,
 * score, statement, template, and test cases come from file storage.</p>
 *
 * <p>Every write keeps the two in step the same way: database changes are
 * flushed first, so a constraint failure surfaces before anything on disk is
 * touched; the files are written next, with an undo registered against the
 * transaction; and the transaction commits only once the files are in place.
 * If anything fails, including the commit itself, the undo puts the files back
 * as they were.</p>
 */
@Service
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

    private static final int DEFAULT_TIME_LIMIT_MS = 1000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;
    private static final int DEFAULT_SCORE = 100;

    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final FileStorageService fileStorageService;
    private final TestCaseCipher cipher;
    private final ContestAccessService access;

    public ProblemService(ProblemRepository problemRepository,
                          SubmissionRepository submissionRepository,
                          FileStorageService fileStorageService,
                          TestCaseCipher cipher,
                          ContestAccessService access) {
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.fileStorageService = fileStorageService;
        this.cipher = cipher;
        this.access = access;
    }

    /** Adds a problem to a contest that has not started yet. */
    @Transactional
    public ProblemResponse add(UUID contestId, UserDetails principal, ProblemRequest request) {
        User user = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);
        access.requireContestOwner(contest, user);
        access.requireProblemsEditable(contest);

        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }

        ProblemId problemId = new ProblemId();
        problemId.setContestId(contestId);
        problemId.setProblemNum(problemRepository.findHighestProblemNum(contestId) + 1);

        Problem problem = new Problem();
        problem.setProblemId(problemId);
        problem.setContest(contest);
        problem.setTestCaseCount(0);
        Problem saved = problemRepository.saveAndFlush(problem);

        ProblemMetadata metadata = new ProblemMetadata(
                request.title().trim(),
                orDefault(request.timeLimitMs(), DEFAULT_TIME_LIMIT_MS),
                orDefault(request.memoryLimitMb(), DEFAULT_MEMORY_LIMIT_MB),
                orDefault(request.score(), DEFAULT_SCORE));

        try {
            if (!fileStorageService.contestExists(contest)) {
                fileStorageService.createContest(contest);
            }
            fileStorageService.createProblemFiles(contest, saved,
                    request.statement() == null ? "" : request.statement(), metadata);
            // Registered after creation, not before: creation cleans up after itself
            // on failure, and an earlier undo could remove a directory this call never made.
            undoOnRollback("creating problem " + problemId.getProblemNum(),
                    () -> deleteFilesIfPresent(contest, saved));
            if (request.solutionTemplate() != null) {
                fileStorageService.saveTemplateSolution(contest, saved, request.solutionTemplate());
            }
        } catch (IOException exception) {
            throw new StorageException("Unable to create problem storage", exception);
        }

        return read(contest, saved);
    }

    @Transactional(readOnly = true)
    public List<ProblemSummary> list(UUID contestId, UserDetails principal) {
        User user = access.currentUser(principal);
        Contest contest = access.requireContest(contestId);
        access.requireProblemReadable(contest, user);

        List<ProblemSummary> summaries = new ArrayList<>();
        for (Problem problem : problemRepository.findByContestId(contestId)) {
            ProblemMetadata metadata = metadata(contest, problem);
            summaries.add(new ProblemSummary(
                    problem.getProblemId().getProblemNum(),
                    metadata.title(),
                    metadata.timeLimitMs(),
                    metadata.memoryLimitMb(),
                    metadata.score(),
                    problem.getTestCaseCount()));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(UUID contestId, int problemNum, UserDetails principal) {
        User user = access.currentUser(principal);
        Problem problem = access.requireProblem(contestId, problemNum);
        Contest contest = problem.getContest();
        access.requireProblemReadable(contest, user);
        return read(contest, problem);
    }

    /** Applies the supplied fields. A null field leaves the stored value alone. */
    @Transactional
    public ProblemResponse update(UUID contestId, int problemNum, UserDetails principal, ProblemRequest request) {
        User user = access.currentUser(principal);
        Problem problem = access.requireProblem(contestId, problemNum);
        Contest contest = problem.getContest();
        access.requireContestOwner(contest, user);
        access.requireProblemsEditable(contest);

        if (request.title() != null && request.title().isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }

        ProblemFiles before = snapshot(contest, problem);
        ProblemMetadata current = before.metadata();
        ProblemMetadata updated = new ProblemMetadata(
                request.title() == null ? current.title() : request.title().trim(),
                orDefault(request.timeLimitMs(), current.timeLimitMs()),
                orDefault(request.memoryLimitMb(), current.memoryLimitMb()),
                orDefault(request.score(), current.score()));

        undoOnRollback("updating problem " + problemNum, () -> restore(contest, problem, before));
        try {
            fileStorageService.saveProblemMetadata(contest, problem, updated);
            if (request.statement() != null) {
                fileStorageService.saveStatement(contest, problem, request.statement());
            }
            if (request.solutionTemplate() != null) {
                fileStorageService.saveTemplateSolution(contest, problem, request.solutionTemplate());
            }
        } catch (IOException exception) {
            throw new StorageException("Unable to update problem storage", exception);
        }

        return read(contest, problem);
    }

    /** Deletes a problem and its submissions. Allowed in any contest state. */
    @Transactional
    public void delete(UUID contestId, int problemNum, UserDetails principal) {
        User user = access.currentUser(principal);
        Problem problem = access.requireProblem(contestId, problemNum);
        Contest contest = problem.getContest();
        access.requireContestOwner(contest, user);

        submissionRepository.deleteByProblem(contest.getContestId(), problem.getProblemId().getProblemNum());
        problemRepository.delete(problem);
        problemRepository.flush();

        if (!fileStorageService.problemExists(contest, problem)) {
            return;
        }
        ProblemFiles before = snapshot(contest, problem);
        undoOnRollback("deleting problem " + problemNum, () -> restore(contest, problem, before));
        try {
            fileStorageService.deleteProblem(contest, problem);
        } catch (IOException exception) {
            throw new StorageException("Unable to delete problem storage", exception);
        }
    }

    /**
     * Returns every test case input for the problem, sample and hidden alike,
     * encrypted for the judging client and with no expected output.
     *
     * <p>The return type carries no output component, so expected output cannot
     * be exposed here by adding a field or a query parameter. Encryption keeps
     * the inputs from being read straight off the API by anything that is not
     * the client; the outputs the client reports are graded server-side in
     * {@code SubmissionService}.</p>
     */
    @Transactional(readOnly = true)
    public List<EncryptedTestCase> testInputs(UUID contestId, int problemNum, UserDetails principal) {
        User user = access.currentUser(principal);
        Problem problem = access.requireProblem(contestId, problemNum);
        Contest contest = problem.getContest();
        access.requireProblemReadable(contest, user);

        List<EncryptedTestCase> inputs = new ArrayList<>();
        try {
            for (TestCase testCase : fileStorageService.readSampleTestCases(contest, problem)) {
                inputs.add(encrypted(testCase));
            }
            for (TestCase testCase : fileStorageService.readHiddenTestCases(contest, problem)) {
                inputs.add(encrypted(testCase));
            }
        } catch (IOException exception) {
            throw new StorageException("Unable to read test cases", exception);
        }
        return inputs;
    }

    private EncryptedTestCase encrypted(TestCase testCase) {
        return new EncryptedTestCase(testCase.id(), cipher.encrypt(testCase.input()));
    }

    /** Replaces both test case collections and refreshes the stored count. */
    @Transactional
    public ProblemResponse replaceTestCases(UUID contestId, int problemNum, UserDetails principal, TestCaseUploadRequest request) {
        User user = access.currentUser(principal);
        Problem problem = access.requireProblem(contestId, problemNum);
        Contest contest = problem.getContest();
        access.requireContestOwner(contest, user);
        access.requireProblemsEditable(contest);

        List<TestCase> samples = toTestCases(request.sampleTestCases());
        List<TestCase> hidden = toTestCases(request.hiddenTestCases());

        problem.setTestCaseCount(samples.size() + hidden.size());
        problemRepository.saveAndFlush(problem);

        ProblemFiles before = snapshot(contest, problem);
        undoOnRollback("replacing test cases for problem " + problemNum, () -> {
            fileStorageService.saveSampleTestCases(contest, problem, before.samples());
            fileStorageService.saveHiddenTestCases(contest, problem, before.hidden());
        });
        try {
            fileStorageService.saveSampleTestCases(contest, problem, samples);
            fileStorageService.saveHiddenTestCases(contest, problem, hidden);
        } catch (IOException exception) {
            throw new StorageException("Unable to store test cases", exception);
        }

        return read(contest, problem);
    }

    private List<TestCase> toTestCases(List<TestCaseData> supplied) {
        if (supplied == null) {
            return List.of();
        }
        List<TestCase> testCases = new ArrayList<>();
        for (TestCaseData data : supplied) {
            if (data.input() == null || data.output() == null) {
                throw new IllegalArgumentException("every test case needs both an input and an expected output");
            }
            testCases.add(new TestCase(data.input(), data.output()));
        }
        return testCases;
    }

    private ProblemResponse read(Contest contest, Problem problem) {
        ProblemMetadata metadata = metadata(contest, problem);
        try {
            List<TestCaseData> samples = fileStorageService.readSampleTestCases(contest, problem).stream()
                    .map(testCase -> new TestCaseData(testCase.input(), testCase.output()))
                    .toList();
            return new ProblemResponse(
                    contest.getContestId(),
                    problem.getProblemId().getProblemNum(),
                    metadata.title(),
                    fileStorageService.readStatement(contest, problem),
                    fileStorageService.readTemplateSolution(contest, problem),
                    metadata.timeLimitMs(),
                    metadata.memoryLimitMb(),
                    metadata.score(),
                    problem.getTestCaseCount(),
                    samples);
        } catch (IOException exception) {
            throw new StorageException("Unable to read problem storage", exception);
        }
    }

    private ProblemMetadata metadata(Contest contest, Problem problem) {
        try {
            return fileStorageService.readProblemMetadata(contest, problem);
        } catch (IOException exception) {
            throw new StorageException("Unable to read problem metadata", exception);
        }
    }

    /** Everything file storage holds for one problem, kept so a rolled-back write can be undone. */
    private record ProblemFiles(ProblemMetadata metadata,
                                String statement,
                                String template,
                                List<TestCase> samples,
                                List<TestCase> hidden) {
    }

    @FunctionalInterface
    private interface FileUndo {
        void run() throws IOException;
    }

    private ProblemFiles snapshot(Contest contest, Problem problem) {
        try {
            return new ProblemFiles(
                    fileStorageService.readProblemMetadata(contest, problem),
                    fileStorageService.readStatement(contest, problem),
                    fileStorageService.readTemplateSolution(contest, problem),
                    fileStorageService.readSampleTestCases(contest, problem),
                    fileStorageService.readHiddenTestCases(contest, problem));
        } catch (IOException exception) {
            throw new StorageException("Unable to read problem storage", exception);
        }
    }

    /** Rewrites the problem directory from a snapshot, whatever state a failed write left it in. */
    private void restore(Contest contest, Problem problem, ProblemFiles files) throws IOException {
        deleteFilesIfPresent(contest, problem);
        fileStorageService.createProblemFiles(contest, problem, files.statement(), files.metadata());
        fileStorageService.saveTemplateSolution(contest, problem, files.template());
        fileStorageService.saveSampleTestCases(contest, problem, files.samples());
        fileStorageService.saveHiddenTestCases(contest, problem, files.hidden());
    }

    private void deleteFilesIfPresent(Contest contest, Problem problem) throws IOException {
        if (fileStorageService.problemExists(contest, problem)) {
            fileStorageService.deleteProblem(contest, problem);
        }
    }

    /**
     * Runs {@code undo} if the current transaction rolls back, so file storage
     * never keeps a change the database discarded.
     *
     * <p>Register it before the file write it undoes: a write that fails halfway
     * still needs undoing.</p>
     */
    private void undoOnRollback(String change, FileUndo undo) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    undo.run();
                } catch (IOException | RuntimeException exception) {
                    log.error("Rolled back {} but could not undo its file changes; "
                            + "file storage no longer matches the database", change, exception);
                }
            }
        });
    }

    private int orDefault(Integer supplied, int fallback) {
        return supplied == null ? fallback : supplied;
    }
}

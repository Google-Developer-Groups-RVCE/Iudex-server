package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.dto.ClientCompilation;
import gdgrvce.iudex.server.dto.ExecutionStatus;
import gdgrvce.iudex.server.dto.SubmissionResult;
import gdgrvce.iudex.server.model.TestCase;
import gdgrvce.iudex.server.model.Verdict;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Turns what the client reported into a verdict.
 *
 * <p>The client controls the whole payload, so nothing it says can pass a test
 * case on its own: a test passes only when its decrypted output matches the
 * expected one. The rules, in order:</p>
 *
 * <ol>
 *   <li>A reported compile failure is {@code COMPILATION_ERROR}.</li>
 *   <li>A result naming a test case twice, or one the problem does not have,
 *       is {@code SYSTEM_ERROR}: otherwise a modified client could manufacture
 *       a passing count from made-up or repeated ids.</li>
 *   <li>Walking the test cases in order (samples, then hidden), the first one
 *       that failed decides: a non-success status maps to its verdict, and a
 *       mismatched output is {@code WRONG_ANSWER}.</li>
 *   <li>A run that otherwise passes but left test cases unreported is
 *       {@code SYSTEM_ERROR}. Only a complete, matching run is
 *       {@code ACCEPTED}.</li>
 * </ol>
 *
 * <p>Deterministic: the same payload against the same test data always grades
 * the same way.</p>
 */
final class Grader {

    /** Compiler diagnostics kept per submission; the column holds this many characters. */
    static final int COMPILER_OUTPUT_LIMIT = 8192;

    record Grade(Verdict verdict, String message, int passed, String compilerOutput) {
    }

    private Grader() {
    }

    /**
     * @param samples   the problem's sample test cases, in order
     * @param hidden    its hidden test cases, in order
     * @param decrypt   decrypts a reported output
     * @throws IllegalArgumentException if a result lacks its id or its output,
     *         which is a malformed request rather than a verdict
     */
    static Grade grade(List<TestCase> samples,
                       List<TestCase> hidden,
                       ClientCompilation compilation,
                       List<SubmissionResult> results,
                       UnaryOperator<String> decrypt) {
        if (compilation != null && !compilation.success()) {
            return new Grade(Verdict.COMPILATION_ERROR, "Compilation failed", 0, diagnostics(compilation));
        }

        // First report per test case stands; later repeats are noted, not used.
        Map<UUID, SubmissionResult> reported = new HashMap<>();
        boolean repeated = false;
        for (SubmissionResult result : results == null ? List.<SubmissionResult>of() : results) {
            if (result == null || result.testCaseId() == null) {
                throw new IllegalArgumentException("every result needs a testCaseId");
            }
            if (result.encryptedOutput() == null) {
                throw new IllegalArgumentException("every result needs an encryptedOutput");
            }
            if (reported.putIfAbsent(result.testCaseId(), result) != null) {
                repeated = true;
            }
        }

        Set<UUID> known = new HashSet<>();
        samples.forEach(testCase -> known.add(testCase.id()));
        hidden.forEach(testCase -> known.add(testCase.id()));
        boolean unknown = !known.containsAll(reported.keySet());

        Verdict firstFailure = null;
        String failureMessage = null;
        int passed = 0;
        int missing = 0;
        for (int index = 0; index < samples.size() + hidden.size(); index++) {
            boolean isSample = index < samples.size();
            TestCase testCase = isSample ? samples.get(index) : hidden.get(index - samples.size());
            SubmissionResult result = reported.get(testCase.id());
            if (result == null) {
                missing++;
                continue;
            }

            Verdict failure = failureOf(result.status());
            if (failure == null && !OutputComparator.matches(decrypt.apply(result.encryptedOutput()),
                    testCase.output())) {
                failure = Verdict.WRONG_ANSWER;
            }
            if (failure == null) {
                passed++;
            } else if (firstFailure == null) {
                firstFailure = failure;
                failureMessage = describe(failure)
                        + (isSample ? " on sample " + (index + 1) : " on a hidden test case");
            }
        }

        if (repeated || unknown) {
            return new Grade(Verdict.SYSTEM_ERROR,
                    "The submission reported a test case more than once, or one this problem does not have",
                    passed, null);
        }
        if (firstFailure != null) {
            return new Grade(firstFailure, failureMessage, passed, null);
        }
        if (missing > 0) {
            return new Grade(Verdict.SYSTEM_ERROR,
                    "No result was reported for " + missing + " test case(s)", passed, null);
        }
        return new Grade(Verdict.ACCEPTED, "Accepted", passed, null);
    }

    private static Verdict failureOf(ExecutionStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case SUCCESS -> null;
            case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
            case TIME_LIMIT_EXCEEDED -> Verdict.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT_EXCEEDED -> Verdict.MEMORY_LIMIT_EXCEEDED;
            case OUTPUT_LIMIT_EXCEEDED -> Verdict.OUTPUT_LIMIT_EXCEEDED;
        };
    }

    private static String describe(Verdict verdict) {
        return switch (verdict) {
            case WRONG_ANSWER -> "Wrong answer";
            case RUNTIME_ERROR -> "Runtime error";
            case TIME_LIMIT_EXCEEDED -> "Time limit exceeded";
            case MEMORY_LIMIT_EXCEEDED -> "Memory limit exceeded";
            case OUTPUT_LIMIT_EXCEEDED -> "Output limit exceeded";
            default -> verdict.name();
        };
    }

    private static String diagnostics(ClientCompilation compilation) {
        StringBuilder output = new StringBuilder();
        for (String stream : new String[]{compilation.stderr(), compilation.stdout()}) {
            if (stream != null && !stream.isBlank()) {
                if (!output.isEmpty()) {
                    output.append('\n');
                }
                output.append(stream.strip());
            }
        }
        return output.length() > COMPILER_OUTPUT_LIMIT
                ? output.substring(0, COMPILER_OUTPUT_LIMIT)
                : output.toString();
    }
}

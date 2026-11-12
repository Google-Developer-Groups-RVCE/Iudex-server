package gdgrvce.iudex.server.dto;

import java.util.UUID;

/**
 * What the contestant's program printed for one test case, encrypted under the
 * shared test data key, and how the run ended.
 *
 * <p>{@code testCaseId} is the identifier the client received alongside the
 * encrypted input, which is how the server pairs an output with the expected
 * one it holds.</p>
 */
public record SubmissionResult(UUID testCaseId, String encryptedOutput, ExecutionStatus status) {

    /** A run that completed normally. */
    public SubmissionResult(UUID testCaseId, String encryptedOutput) {
        this(testCaseId, encryptedOutput, ExecutionStatus.SUCCESS);
    }
}

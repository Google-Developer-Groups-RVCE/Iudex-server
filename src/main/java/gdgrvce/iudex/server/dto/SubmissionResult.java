package gdgrvce.iudex.server.dto;

import java.util.UUID;

/**
 * What the contestant's program printed for one test case, encrypted under the
 * shared test data key.
 *
 * <p>{@code testCaseId} is the identifier the client received alongside the
 * encrypted input, which is how the server pairs an output with the expected
 * one it holds.</p>
 */
public record SubmissionResult(UUID testCaseId, String encryptedOutput) {
}

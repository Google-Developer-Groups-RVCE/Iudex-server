package gdgrvce.iudex.server.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The outcome of a submission.
 *
 * <p>Reports how many test cases passed, never which ones: naming the failures
 * would tell a contestant the shape of the hidden data.</p>
 */
public record SubmissionResponse(UUID submissionId,
                                 UUID problemId,
                                 UUID contestId,
                                 int problemNum,
                                 int submissionNum,
                                 UUID userId,
                                 String username,
                                 int passedTestCaseCount,
                                 int testCaseCount,
                                 OffsetDateTime receivedAt,
                                 Integer clientDurationMs) {
}

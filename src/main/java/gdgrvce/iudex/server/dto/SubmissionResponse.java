package gdgrvce.iudex.server.dto;

import gdgrvce.iudex.server.model.Verdict;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The outcome of a submission.
 *
 * <p>The message names a failing test only when it is a sample, which is
 * public anyway; a hidden failure is reported without saying which one, since
 * that would describe the shape of the hidden data. The source code is not
 * here: it is read separately, and only the latest is kept.</p>
 *
 * <p>{@code compilerOutput} is set only for a compilation error, and is the
 * contestant's own compiler talking.</p>
 */
public record SubmissionResponse(UUID submissionId,
                                 UUID contestId,
                                 int problemNum,
                                 int submissionNum,
                                 UUID userId,
                                 String username,
                                 String language,
                                 Verdict verdict,
                                 String message,
                                 int passedTestCaseCount,
                                 int testCaseCount,
                                 OffsetDateTime receivedAt,
                                 Integer clientDurationMs,
                                 String compilerOutput) {
}

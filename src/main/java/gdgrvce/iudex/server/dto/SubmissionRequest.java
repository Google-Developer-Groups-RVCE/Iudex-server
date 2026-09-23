package gdgrvce.iudex.server.dto;

import java.util.List;
import java.util.UUID;

/**
 * One attempt at a problem: the outputs the client captured while running the
 * contestant's program against the test cases it was given.
 *
 * <p>No score is accepted from the client. The server grades these outputs
 * against the expected ones it holds and derives the passed count itself.</p>
 *
 * <p>{@code clientDurationMs} is optional telemetry. It is stored for display
 * and never influences a verdict or a ranking.</p>
 */
public record SubmissionRequest(UUID problemId,
                                List<SubmissionResult> results,
                                Integer clientDurationMs) {
}

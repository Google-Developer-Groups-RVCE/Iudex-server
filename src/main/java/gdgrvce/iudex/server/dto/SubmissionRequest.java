package gdgrvce.iudex.server.dto;

import java.util.List;
import java.util.UUID;

/**
 * One attempt at a problem: the source code, the client's compile result, and
 * the outputs it captured while running the program against the test cases it
 * was given.
 *
 * <p>No score or verdict is accepted from the client. The server grades these
 * outputs against the expected ones it holds and derives both itself.</p>
 *
 * <p>{@code compilation} is optional (interpreted languages have none), and so
 * is {@code clientDurationMs}, which is telemetry: stored for display, never
 * an input to a verdict or a ranking.</p>
 */
public record SubmissionRequest(UUID contestId,
                                Integer problemNum,
                                String language,
                                String sourceCode,
                                ClientCompilation compilation,
                                List<SubmissionResult> results,
                                Integer clientDurationMs) {
}

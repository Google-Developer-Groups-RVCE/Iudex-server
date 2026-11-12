package gdgrvce.iudex.server.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A problem as returned to a caller who is allowed to read it.
 *
 * <p>Sample test cases carry their expected output because samples are public.
 * Hidden cases never appear here at all, and neither does the reference
 * solution, which has its own contestmaster-only endpoint.</p>
 *
 * <p>{@code solutionTemplates} is keyed by language id; a language with no
 * template is simply absent.</p>
 */
public record ProblemResponse(UUID contestId,
                              int problemNum,
                              String title,
                              String statement,
                              Map<String, String> solutionTemplates,
                              int timeLimitMs,
                              int memoryLimitMb,
                              int score,
                              int testCaseCount,
                              List<TestCaseData> sampleTestCases) {
}

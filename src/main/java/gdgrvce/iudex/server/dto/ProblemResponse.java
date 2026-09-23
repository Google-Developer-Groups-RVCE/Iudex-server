package gdgrvce.iudex.server.dto;

import java.util.List;
import java.util.UUID;

/**
 * A problem as returned to a caller who is allowed to read it.
 *
 * <p>Sample test cases carry their expected output because samples are public.
 * Hidden cases never appear here at all.</p>
 */
public record ProblemResponse(UUID problemId,
                              UUID contestId,
                              int problemNum,
                              String title,
                              String statement,
                              String solutionTemplate,
                              int timeLimitMs,
                              int memoryLimitMb,
                              int score,
                              int testCaseCount,
                              List<TestCaseData> sampleTestCases) {
}

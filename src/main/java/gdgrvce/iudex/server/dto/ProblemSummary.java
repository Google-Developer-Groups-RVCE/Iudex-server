package gdgrvce.iudex.server.dto;

import java.util.UUID;

/** A problem as listed inside a contest, without the statement body. */
public record ProblemSummary(UUID problemId,
                             int problemNum,
                             String title,
                             int timeLimitMs,
                             int memoryLimitMb,
                             int score,
                             int testCaseCount) {
}

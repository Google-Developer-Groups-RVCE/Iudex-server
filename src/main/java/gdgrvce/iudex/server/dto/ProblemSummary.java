package gdgrvce.iudex.server.dto;

/** A problem as listed inside a contest, without the statement body. */
public record ProblemSummary(int problemNum,
                             String title,
                             int timeLimitMs,
                             int memoryLimitMb,
                             int score,
                             int testCaseCount) {
}

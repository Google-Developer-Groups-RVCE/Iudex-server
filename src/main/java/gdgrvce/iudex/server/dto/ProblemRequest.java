package gdgrvce.iudex.server.dto;

/**
 * The fields a contestmaster supplies when adding or updating a problem.
 *
 * <p>On update, a null field leaves the stored value alone.</p>
 */
public record ProblemRequest(String title,
                             String statement,
                             String solutionTemplate,
                             Integer timeLimitMs,
                             Integer memoryLimitMb,
                             Integer score) {
}

package gdgrvce.iudex.server.dto;

import java.util.Map;

/**
 * The fields a contestmaster supplies when adding or updating a problem.
 *
 * <p>On update, a null field leaves the stored value alone.</p>
 *
 * <p>{@code solutionTemplates} maps a language id ({@code cpp}, {@code java},
 * {@code python}) to the starting code a contestant gets in that language:
 * typically the program that reads the input and calls an empty function for
 * them to fill in. Entries are merged into what is stored; a blank value
 * removes that language's template.</p>
 */
public record ProblemRequest(String title,
                             String statement,
                             Map<String, String> solutionTemplates,
                             Integer timeLimitMs,
                             Integer memoryLimitMb,
                             Integer score) {
}

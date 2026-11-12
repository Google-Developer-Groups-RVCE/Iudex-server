package gdgrvce.iudex.server.dto;

import java.util.UUID;

/**
 * A reference solution, as returned to the contestmaster.
 *
 * <p>Deliberately its own type: no contestant-facing response has a field
 * that could carry it.</p>
 */
public record SolutionResponse(UUID contestId, int problemNum, String language, String sourceCode) {
}

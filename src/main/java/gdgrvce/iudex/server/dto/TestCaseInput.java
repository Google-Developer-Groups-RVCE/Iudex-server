package gdgrvce.iudex.server.dto;

import java.util.UUID;

/**
 * A test case input as sent to a contestant by {@code GET /api/problems/{id}/tests}.
 *
 * <p>This record has no expected-output component, so no change to a
 * controller or a query parameter can make that field appear in a response.</p>
 */
public record TestCaseInput(UUID id, String inputData) {
}

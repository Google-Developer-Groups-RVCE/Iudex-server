package gdgrvce.iudex.server.dto;

import java.util.UUID;

/**
 * A test case input as delivered to the judging client by
 * {@code GET /api/problems/{id}/tests}, encrypted under the shared test data key.
 *
 * <p>Sample and hidden cases are delivered alike and are indistinguishable here.
 * This record has no expected-output component, so no change to a controller or
 * a query parameter can make that field appear in a response.</p>
 */
public record EncryptedTestCase(UUID id, String encryptedInput) {
}

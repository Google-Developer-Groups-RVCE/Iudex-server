package gdgrvce.iudex.server.model;

import java.util.Objects;
import java.util.UUID;

/**
 * One input/output pair used to evaluate a problem.
 *
 * <p>The identifier is stable across edits to the surrounding collection, so a
 * submission result can name the test case it ran. A null identifier is
 * assigned one on construction.</p>
 *
 * <p>{@code output} is confidential: it must never reach a contestant-facing
 * response. Serialize {@link #input} through a separate view instead of
 * returning this record.</p>
 */
public record TestCase(UUID id, String input, String output) {

    public TestCase {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public TestCase(String input, String output) {
        this(null, input, output);
    }
}

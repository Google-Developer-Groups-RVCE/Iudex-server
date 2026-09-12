package gdgrvce.iudex.server.model;

import java.util.Objects;

/**
 * One input/output pair used to evaluate a problem.
 */
public record TestCase(String input, String output) {

    public TestCase {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
    }
}
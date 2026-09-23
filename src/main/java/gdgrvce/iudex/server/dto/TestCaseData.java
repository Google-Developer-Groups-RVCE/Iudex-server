package gdgrvce.iudex.server.dto;

/**
 * An input paired with its expected output.
 *
 * <p>Only ever serialized for sample cases, which are public. Hidden cases
 * reach a contestant as {@link TestCaseInput}, which carries no output.</p>
 */
public record TestCaseData(String input, String output) {
}

package gdgrvce.iudex.server.dto;

/**
 * The client's compile step. Absent for interpreted languages.
 *
 * <p>Both streams are kept because compilers disagree about which one carries
 * the diagnostics.</p>
 */
public record ClientCompilation(boolean success, String stdout, String stderr) {
}

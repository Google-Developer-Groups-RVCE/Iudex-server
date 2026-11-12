package gdgrvce.iudex.server.dto;

/** A contestmaster's reference solution for a problem. */
public record SolutionRequest(String language, String sourceCode) {
}

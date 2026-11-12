package gdgrvce.iudex.server.model;

import java.util.Objects;

/**
 * The contestmaster's own solution to a problem, held in file storage.
 *
 * <p>Confidential: it never reaches a contestant. It is kept for the
 * contestmaster's reference and for post-contest analysis.</p>
 */
public record ReferenceSolution(String language, String sourceCode) {

    public ReferenceSolution {
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
    }
}

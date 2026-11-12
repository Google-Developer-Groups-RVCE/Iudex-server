package gdgrvce.iudex.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Caps the size of stored source code: submissions and reference solutions.
 *
 * <p>The cap is in UTF-8 bytes. The database column holds 65536 characters,
 * and a character is at least one byte, so the cap may not exceed that.</p>
 */
@Component
public class SourceCodeLimit {

    static final int COLUMN_CHARACTERS = 65536;

    private final int maxBytes;

    public SourceCodeLimit(@Value("${iudex.source.max-bytes:65536}") int maxBytes) {
        if (maxBytes <= 0 || maxBytes > COLUMN_CHARACTERS) {
            throw new IllegalStateException(
                    "iudex.source.max-bytes must be between 1 and " + COLUMN_CHARACTERS + ", was " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    /** Requires non-blank source within the cap. */
    public void check(String field, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        int bytes = sourceCode.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > maxBytes) {
            throw new IllegalArgumentException(
                    field + " is " + bytes + " bytes; the limit is " + maxBytes);
        }
    }
}

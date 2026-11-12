package gdgrvce.iudex.server.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * The languages a submission, a template, or a reference solution may be
 * written in. The wire form is the lowercase {@link #id()}, which is also what
 * the judging client calls each language.
 */
public enum Language {
    CPP("cpp"),
    JAVA("java"),
    PYTHON("python");

    private final String id;

    Language(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /**
     * Resolves a wire id, case-insensitively.
     *
     * @throws IllegalArgumentException naming the supported ids, so a client
     *         sending something else learns what to send instead
     */
    public static Language fromId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("language is required");
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(language -> language.id.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unsupported language '" + id + "'; expected one of " + supportedIds()));
    }

    public static String supportedIds() {
        return String.join(", ", Arrays.stream(values()).map(Language::id).toList());
    }
}

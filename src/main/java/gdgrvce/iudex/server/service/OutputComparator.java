package gdgrvce.iudex.server.service;

/**
 * Compares a user's output against the expected output under lenient whitespace rules:
 * line endings are unified, trailing whitespace on each line is ignored, and trailing
 * blank lines are ignored. Differences within a line still fail the match.
 */
final class OutputComparator {

    private OutputComparator() {
    }

    static boolean matches(String actual, String expected) {
        return normalize(actual).equals(normalize(expected));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(stripTrailing(line)).append('\n');
        }
        return stripTrailingNewlines(builder.toString());
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    private static String stripTrailingNewlines(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '\n') {
            end--;
        }
        return value.substring(0, end);
    }
}

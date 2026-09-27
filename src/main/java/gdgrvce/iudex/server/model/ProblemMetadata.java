package gdgrvce.iudex.server.model;

import java.util.Objects;

/**
 * The problem fields held in file storage rather than the database.
 *
 * <p>The schema keeps only identity and ordering for a problem, so title,
 * limits, and score live here alongside the statement and template.</p>
 */
public record ProblemMetadata(String title, int timeLimitMs, int memoryLimitMb, int score) {

    public ProblemMetadata {
        Objects.requireNonNull(title, "title must not be null");
        if (timeLimitMs <= 0) {
            throw new IllegalArgumentException("time limit must be positive");
        }
        if (memoryLimitMb <= 0) {
            throw new IllegalArgumentException("memory limit must be positive");
        }
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
    }
}

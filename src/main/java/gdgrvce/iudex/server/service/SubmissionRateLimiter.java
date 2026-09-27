package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces a hard minimum interval between a single user's submissions across all problems.
 * The check and the timestamp update happen atomically so concurrent requests cannot both
 * pass the gate.
 */
@Component
public class SubmissionRateLimiter {

    private final long minIntervalMs;
    private final ConcurrentHashMap<UUID, Long> lastSubmitMs = new ConcurrentHashMap<>();

    public SubmissionRateLimiter(@Value("${iudex.submission.min-interval-ms:2000}") long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    /** Records a submission attempt, or throws if the user is submitting too soon. */
    public void check(UUID userId) {
        long now = System.currentTimeMillis();
        long[] retryAfterMs = {0};
        lastSubmitMs.compute(userId, (key, last) -> {
            if (last != null && now - last < minIntervalMs) {
                retryAfterMs[0] = minIntervalMs - (now - last);
                return last;
            }
            return now;
        });
        if (retryAfterMs[0] > 0) {
            long retryAfterSeconds = Math.max(1, (retryAfterMs[0] + 999) / 1000);
            throw new RateLimitExceededException(
                    "Too many submissions. Please wait before submitting again.", retryAfterSeconds);
        }
    }
}

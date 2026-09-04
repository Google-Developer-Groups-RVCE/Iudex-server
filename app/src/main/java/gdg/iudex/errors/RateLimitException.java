package gdg.iudex.errors;

import java.time.Duration;

/**
 *  class RateLimitException
 *
 *  Thrown when a client has made too many failed attempts.
 *
 *  @retryAfter - how long the caller must wait before trying again.
 *                Reported to the client in the Retry-After header.
 */

public final class RateLimitException extends ApiException {

    private final Duration retryAfter;

    public RateLimitException(Duration retryAfter) {
        super(429, "Too many attempts, please try again later");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}

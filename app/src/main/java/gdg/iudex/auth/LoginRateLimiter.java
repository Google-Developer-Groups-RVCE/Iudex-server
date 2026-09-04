package gdg.iudex.auth;

import gdg.iudex.errors.RateLimitException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  class LoginRateLimiter
 *
 *  Counts failed login attempts per key within a rolling window and
 *  refuses further attempts once the budget is spent.
 *
 *  This guards two different things. It slows password guessing, and
 *  more importantly it caps how much Argon2 hashing a stranger can
 *  make the server do: hashing is deliberately expensive, so without
 *  a limit a burst of wrong passwords is enough to exhaust the CPU.
 *  checkAllowed is therefore called BEFORE any hashing happens.
 *
 *  Only failures count, so ordinary users never run out of budget.
 *
 *  Methods:
 *  checkAllowed - throw if this key is currently locked out
 *  recordFailure - count a failed attempt
 *  clear - forget a key's failures (call on a successful login)
 *  purgeExpired - drop keys whose windows have elapsed
 */

public final class LoginRateLimiter {

    private record Attempts(int count, Instant resetAt) {}

    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    private final ConcurrentHashMap<String, Attempts> attempts =
            new ConcurrentHashMap<>();

    public LoginRateLimiter(int maxFailures, Duration window) {
        this(maxFailures, window, Clock.systemUTC());
    }

    // Clock is injectable so tests do not have to sleep.
    public LoginRateLimiter(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public void checkAllowed(String key) {
        Attempts current = attempts.get(key);

        if (current == null) {
            return;
        }

        Instant now = clock.instant();

        // Window elapsed, this key gets a clean slate.
        if (!now.isBefore(current.resetAt())) {
            attempts.remove(key, current);
            return;
        }

        if (current.count() >= maxFailures) {
            throw new RateLimitException(
                Duration.between(now, current.resetAt())
            );
        }
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();

        attempts.compute(key, (ignored, current) ->
            (current == null || !now.isBefore(current.resetAt()))
                ? new Attempts(1, now.plus(window))
                : new Attempts(current.count() + 1, current.resetAt())
        );
    }

    public void clear(String key) {
        attempts.remove(key);
    }

    /** Stops the map growing without bound on a long-running server. */
    public void purgeExpired() {
        Instant now = clock.instant();

        attempts.entrySet().removeIf(entry ->
            !now.isBefore(entry.getValue().resetAt())
        );
    }
}

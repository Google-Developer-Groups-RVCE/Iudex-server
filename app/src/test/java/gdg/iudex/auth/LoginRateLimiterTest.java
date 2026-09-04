package gdg.iudex.auth;

import gdg.iudex.errors.RateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  class LoginRateLimiterTest
 *
 *  Unit tests for LoginRateLimiter.
 *
 *  The limiter takes a Clock so these can move time forward directly
 *  instead of sleeping through a real window.
 *
 *  Tests:
 *  allowsAttemptsUnderTheLimit - a few failures are fine.
 *  blocksOnceTheLimitIsReached - the next attempt is refused.
 *  keysAreIndependent - one key's failures do not lock out another.
 *  windowExpires - the block lifts once the window elapses.
 *  successClearsFailures - a good login resets the budget.
 *  purgeExpiredDropsElapsedWindows - the map does not grow forever.
 */

class LoginRateLimiterTest {

    /** A clock the test can wind forward. */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final MutableClock clock = new MutableClock();

    private final LoginRateLimiter limiter =
        new LoginRateLimiter(3, Duration.ofMinutes(15), clock);

    @Test
    void allowsAttemptsUnderTheLimit() {

        limiter.recordFailure("alice");
        limiter.recordFailure("alice");

        assertDoesNotThrow(() -> limiter.checkAllowed("alice"));
    }

    @Test
    void blocksOnceTheLimitIsReached() {

        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("alice");
        }

        RateLimitException thrown = assertThrows(
            RateLimitException.class,
            () -> limiter.checkAllowed("alice")
        );

        assertEquals(429, thrown.status());
        assertFalse(thrown.retryAfter().isNegative());
    }

    @Test
    void keysAreIndependent() {

        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("alice");
        }

        assertThrows(
            RateLimitException.class,
            () -> limiter.checkAllowed("alice")
        );

        assertDoesNotThrow(() -> limiter.checkAllowed("bob"),
            "One locked out account must not lock out everyone else");
    }

    @Test
    void windowExpires() {

        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("alice");
        }

        assertThrows(
            RateLimitException.class,
            () -> limiter.checkAllowed("alice")
        );

        clock.advance(Duration.ofMinutes(16));

        assertDoesNotThrow(() -> limiter.checkAllowed("alice"),
            "The block should lift once the window has elapsed");
    }

    @Test
    void successClearsFailures() {

        limiter.recordFailure("alice");
        limiter.recordFailure("alice");

        // as a successful login would
        limiter.clear("alice");

        limiter.recordFailure("alice");
        limiter.recordFailure("alice");

        assertDoesNotThrow(() -> limiter.checkAllowed("alice"),
            "Failures before a successful login should not count");
    }

    @Test
    void purgeExpiredDropsElapsedWindows() {

        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("alice");
        }

        clock.advance(Duration.ofMinutes(16));
        limiter.purgeExpired();

        assertDoesNotThrow(() -> limiter.checkAllowed("alice"));
    }
}

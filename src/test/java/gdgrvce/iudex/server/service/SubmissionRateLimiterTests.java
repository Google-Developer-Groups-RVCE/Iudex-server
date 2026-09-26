package gdgrvce.iudex.server.service;

import gdgrvce.iudex.server.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionRateLimiterTests {

    @Test
    void firstSubmissionIsAllowedAndImmediateResubmissionIsRejected() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(10_000);
        UUID user = UUID.randomUUID();

        assertDoesNotThrow(() -> limiter.check(user));
        assertThrows(RateLimitExceededException.class, () -> limiter.check(user));
    }

    @Test
    void differentUsersAreLimitedIndependently() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(10_000);

        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID()));
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID()));
    }

    @Test
    void zeroIntervalNeverThrottles() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(0);
        UUID user = UUID.randomUUID();

        assertDoesNotThrow(() -> limiter.check(user));
        assertDoesNotThrow(() -> limiter.check(user));
    }

    @Test
    void rejectionReportsAPositiveRetryAfter() {
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(10_000);
        UUID user = UUID.randomUUID();
        limiter.check(user);

        RateLimitExceededException exception =
                assertThrows(RateLimitExceededException.class, () -> limiter.check(user));
        assertTrue(exception.getRetryAfterSeconds() >= 1);
    }
}

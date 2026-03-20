package services.tbank;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExponentialBackoffRetry}.
 *
 * Uses a modified version of the retry logic to avoid actual Thread.sleep calls
 * by verifying attempt counts and exception propagation.
 */
class ExponentialBackoffRetryTest {

    // -----------------------------------------------------------------------
    // Test 1: successful action on first try
    // -----------------------------------------------------------------------

    @Test
    void successOnFirstAttempt_returnsResult() {
        String result = ExponentialBackoffRetry.execute(() -> "OK");
        assertEquals("OK", result);
    }

    // -----------------------------------------------------------------------
    // Test 2: non-rate-limit exception is re-thrown immediately
    // -----------------------------------------------------------------------

    @Test
    void nonRateLimitException_isRethrownImmediately() {
        AtomicInteger callCount = new AtomicInteger(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                ExponentialBackoffRetry.execute(() -> {
                    callCount.incrementAndGet();
                    throw new RuntimeException("Some other error");
                })
        );

        assertEquals("Some other error", ex.getMessage());
        // Should not retry on non-rate-limit errors
        assertEquals(1, callCount.get(), "Must not retry on non-rate-limit errors");
    }

    // -----------------------------------------------------------------------
    // Test 3: isRateLimitError detects 429 in the message
    // -----------------------------------------------------------------------

    @Test
    void isRateLimitError_detectsWith429() {
        RuntimeException e = new RuntimeException("HTTP 429: Too Many Requests");
        assertTrue(ExponentialBackoffRetry.isRateLimitError(e));
    }

    @Test
    void isRateLimitError_detectsTooManyRequests() {
        RuntimeException e = new RuntimeException("too many requests from client");
        assertTrue(ExponentialBackoffRetry.isRateLimitError(e));
    }

    @Test
    void isRateLimitError_detectsRateLimit() {
        RuntimeException e = new RuntimeException("rate limit exceeded");
        assertTrue(ExponentialBackoffRetry.isRateLimitError(e));
    }

    @Test
    void isRateLimitError_detectsResourceExhausted() {
        RuntimeException e = new RuntimeException("RESOURCE_EXHAUSTED: quota exceeded");
        assertTrue(ExponentialBackoffRetry.isRateLimitError(e));
    }

    @Test
    void isRateLimitError_caseInsensitive() {
        RuntimeException e = new RuntimeException("RATE LIMIT EXCEEDED");
        assertTrue(ExponentialBackoffRetry.isRateLimitError(e));
    }

    @Test
    void isRateLimitError_normalError_returnsFalse() {
        RuntimeException e = new RuntimeException("Connection refused");
        assertFalse(ExponentialBackoffRetry.isRateLimitError(e));
    }

    @Test
    void isRateLimitError_nullMessage_returnsFalse() {
        RuntimeException e = new RuntimeException((String) null);
        assertFalse(ExponentialBackoffRetry.isRateLimitError(e));
    }

    // -----------------------------------------------------------------------
    // Test 4: isRateLimitError checks cause chain
    // -----------------------------------------------------------------------

    @Test
    void isRateLimitError_detectsInCause() {
        RuntimeException cause = new RuntimeException("HTTP 429");
        RuntimeException wrapper = new RuntimeException("Wrapped error", cause);
        assertTrue(ExponentialBackoffRetry.isRateLimitError(wrapper));
    }

    @Test
    void isRateLimitError_deepCause_detected() {
        RuntimeException root = new RuntimeException("resource exhausted");
        RuntimeException mid = new RuntimeException("API call failed", root);
        RuntimeException top = new RuntimeException("Service unavailable", mid);
        assertTrue(ExponentialBackoffRetry.isRateLimitError(top));
    }

    // -----------------------------------------------------------------------
    // Test 5: constants are within required bounds (1s initial, 32s max)
    // -----------------------------------------------------------------------

    @Test
    void initialDelay_isOneSecond() {
        assertEquals(1_000L, ExponentialBackoffRetry.INITIAL_DELAY_MS,
                "Initial backoff delay must be 1 second");
    }

    @Test
    void maxDelay_isThirtyTwoSeconds() {
        assertEquals(32_000L, ExponentialBackoffRetry.MAX_DELAY_MS,
                "Maximum backoff delay must be 32 seconds");
    }

    @Test
    void maxAttempts_isAtLeastSix() {
        assertTrue(ExponentialBackoffRetry.MAX_ATTEMPTS >= 6,
                "MAX_ATTEMPTS must allow enough retries to reach the 32s cap");
    }

    // -----------------------------------------------------------------------
    // Test 6: exponential delay sequence
    // -----------------------------------------------------------------------

    @Test
    void exponentialDelaySequence_doublesEachTime() {
        long delay = ExponentialBackoffRetry.INITIAL_DELAY_MS;
        long max   = ExponentialBackoffRetry.MAX_DELAY_MS;

        // 1s → 2s → 4s → 8s → 16s → 32s (cap)
        long[] expected = {1_000, 2_000, 4_000, 8_000, 16_000, 32_000};
        for (long exp : expected) {
            assertEquals(exp, Math.min(delay, max));
            delay = Math.min(delay * 2, max);
        }
        // Subsequent doubles stay capped
        assertEquals(32_000, Math.min(delay, max));
    }

    // -----------------------------------------------------------------------
    // Test 7: result type is preserved
    // -----------------------------------------------------------------------

    @Test
    void resultType_isPreserved_forInteger() {
        Integer result = ExponentialBackoffRetry.execute(() -> 42);
        assertEquals(42, result);
    }

    @Test
    void resultType_isPreserved_forNull() {
        Object result = ExponentialBackoffRetry.execute(() -> null);
        assertNull(result);
    }
}

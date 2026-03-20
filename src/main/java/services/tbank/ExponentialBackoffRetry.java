package services.tbank;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for retrying an operation with exponential backoff on rate-limit errors.
 *
 * <p>Strategy: start with {@code initialDelayMs}, double on each attempt, cap at
 * {@code maxDelayMs}, stop after {@code maxAttempts} attempts total.
 *
 * <p>Recognises rate-limit conditions by checking if the exception message contains
 * "429", "rate", "too many requests", or "resource exhausted" (case-insensitive).
 */
public class ExponentialBackoffRetry {

    private static final Logger log = LoggerFactory.getLogger(ExponentialBackoffRetry.class);

    /** Initial delay: 1 second */
    static final long INITIAL_DELAY_MS = 1_000L;
    /** Maximum delay: 32 seconds */
    static final long MAX_DELAY_MS = 32_000L;
    /** Maximum number of attempts (1 initial + 5 retries gives delays: 1, 2, 4, 8, 16 s) */
    static final int MAX_ATTEMPTS = 6;

    private ExponentialBackoffRetry() {
    }

    /**
     * Execute {@code action} with exponential backoff on rate-limit errors.
     *
     * @param action the operation to perform
     * @param <T>    return type
     * @return the result of {@code action}
     * @throws RuntimeException if all retries are exhausted or a non-rate-limit error occurs
     */
    public static <T> T execute(Supplier<T> action) {
        long delayMs = INITIAL_DELAY_MS;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return action.get();
            } catch (Exception ex) {
                if (!isRateLimitError(ex)) {
                    throw ex;
                }
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Rate limit: all {} attempts exhausted. Last error: {}", MAX_ATTEMPTS, ex.getMessage());
                    throw ex;
                }
                long sleepMs = Math.min(delayMs, MAX_DELAY_MS);
                log.warn("Rate limit detected (attempt {}/{}). Retrying in {} ms. Error: {}",
                        attempt, MAX_ATTEMPTS, sleepMs, ex.getMessage());
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during backoff sleep", ie);
                }
                delayMs = Math.min(delayMs * 2, MAX_DELAY_MS);
            }
        }
    }

    /**
     * Returns true if the exception (or any of its causes) looks like a rate-limit / 429 error.
     */
    static boolean isRateLimitError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            String msg = current.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("429")
                        || lower.contains("too many requests")
                        || lower.contains("rate limit")
                        || lower.contains("resource exhausted")
                        || lower.contains("resource_exhausted")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}

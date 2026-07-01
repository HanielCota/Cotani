package com.cotani.task.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void fixedPolicyHasDefaultValues() {
        RetryPolicy policy = RetryPolicy.fixed(3, Duration.ofMillis(50));

        assertEquals(3, policy.maxAttempts());
        assertEquals(Duration.ofMillis(50), policy.delay());
        assertEquals(1.0, policy.backoffMultiplier());
        assertEquals(0.0, policy.jitterFactor());
        assertFalse(policy.symmetricJitter());
        assertTrue(policy.retryIf().test(new RuntimeException()));
    }

    @Test
    void exponentialPolicyDoublesBackoff() {
        RetryPolicy policy = RetryPolicy.exponential(3, Duration.ofMillis(10));

        assertEquals(2.0, policy.backoffMultiplier());
        assertEquals(0.0, policy.jitterFactor());
    }

    @Test
    void exponentialWithJitterSetsFactor() {
        RetryPolicy policy = RetryPolicy.exponentialWithJitter(3, Duration.ofMillis(10), 0.2);

        assertEquals(0.2, policy.jitterFactor());
    }

    @Test
    void retryIfReplacesPredicate() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        RetryPolicy policy = RetryPolicy.fixed(1, Duration.ZERO).retryIf(error -> {
            captured.set(error);
            return false;
        });

        RuntimeException cause = new RuntimeException("boom");
        assertFalse(policy.retryIf().test(cause));
        assertEquals(cause, captured.get());
    }

    @Test
    void delayMillisForAttemptRespectsBackoff() {
        RetryPolicy policy = RetryPolicy.exponential(3, Duration.ofMillis(10));

        assertEquals(10, policy.delayMillisForAttempt(1));
        assertEquals(20, policy.delayMillisForAttempt(2));
        assertEquals(40, policy.delayMillisForAttempt(3));
    }

    @Test
    void delayMillisForAttemptWithAsymmetricJitterReducesDelay() {
        RetryPolicy policy = RetryPolicy.fixed(2, Duration.ofMillis(100)).withJitter(1.0);

        long delay = policy.delayMillisForAttempt(1);
        assertTrue(delay >= 0 && delay <= 100, "Asymmetric jitter should reduce delay to [0, base]");
    }

    @Test
    void delayMillisForAttemptWithSymmetricJitterVariatesAroundBase() {
        RetryPolicy policy =
                RetryPolicy.fixed(2, Duration.ofMillis(100)).withJitter(0.5).withSymmetricJitter();

        long delay = policy.delayMillisForAttempt(1);
        assertTrue(delay >= 50 && delay <= 150, "Symmetric jitter should vary around base delay");
    }

    @Test
    void rejectsZeroMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.fixed(0, Duration.ZERO));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullDelay() {
        assertThrows(NullPointerException.class, () -> new RetryPolicy(1, null, 1.0, 0.0, error -> true, false));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullRetryIf() {
        assertThrows(NullPointerException.class, () -> new RetryPolicy(1, Duration.ZERO, 1.0, 0.0, null, false));
    }

    @Test
    void rejectsNegativeJitter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RetryPolicy.fixed(1, Duration.ZERO).withJitter(-0.1));
    }

    @Test
    void rejectsJitterAboveOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RetryPolicy.fixed(1, Duration.ZERO).withJitter(1.1));
    }

    @Test
    void rejectsZeroBackoffMultiplier() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetryPolicy(2, Duration.ofMillis(10), 0, 0, error -> true, false));
    }
}

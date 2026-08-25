package com.cotani.job.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JobRetryPolicyTest {
    @Test
    void calculatesBoundedExponentialBackoff() {
        var policy = new JobRetryPolicy(4, Duration.ofSeconds(1), Duration.ofSeconds(3), 2.0d);

        assertEquals(Duration.ofSeconds(1), policy.delayBeforeNextAttempt(1));
        assertEquals(Duration.ofSeconds(2), policy.delayBeforeNextAttempt(2));
        assertEquals(Duration.ofSeconds(3), policy.delayBeforeNextAttempt(3));
    }

    @Test
    void rejectsAttemptsOutsideRetryWindow() {
        var policy = JobRetryPolicy.defaults();

        assertThrows(IllegalArgumentException.class, () -> policy.delayBeforeNextAttempt(0));
        assertThrows(IllegalArgumentException.class, () -> policy.delayBeforeNextAttempt(policy.maxAttempts()));
    }
}

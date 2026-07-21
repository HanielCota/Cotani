package com.cotani.task.throttle;

public final class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RateLimitExceededException(int maxAttempts) {
        super("Rate limit could not be acquired after " + maxAttempts + " attempt(s)");
    }
}

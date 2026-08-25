package com.cotani.reward.api;

/** Base exception for expected reward-domain failures. */
public class RewardException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RewardException(String message) {
        super(message);
    }

    public RewardException(String message, Throwable cause) {
        super(message, cause);
    }
}

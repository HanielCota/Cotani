package com.cotani.achievement.api;

/** Base exception for expected achievement-domain failures. */
public class AchievementException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AchievementException(String message) {
        super(message);
    }

    public AchievementException(String message, Throwable cause) {
        super(message, cause);
    }
}

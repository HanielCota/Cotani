package com.cotani.season.api;

/** Base exception for expected season-domain failures. */
public class SeasonException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SeasonException(String message) {
        super(message);
    }

    public SeasonException(String message, Throwable cause) {
        super(message, cause);
    }
}

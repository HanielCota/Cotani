package com.cotani.location.api;

/** Base exception for expected location-domain failures. */
public class LocationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LocationException(String message) {
        super(message);
    }

    public LocationException(String message, Throwable cause) {
        super(message, cause);
    }
}

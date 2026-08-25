package com.cotani.statistics.api;

/** Base exception for expected statistics-domain failures. */
public class StatisticException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StatisticException(String message) {
        super(message);
    }

    public StatisticException(String message, Throwable cause) {
        super(message, cause);
    }
}

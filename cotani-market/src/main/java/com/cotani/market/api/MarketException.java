package com.cotani.market.api;

/** Base exception for expected marketplace domain failures. */
public class MarketException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MarketException(String message) {
        super(message);
    }

    public MarketException(String message, Throwable cause) {
        super(message, cause);
    }
}

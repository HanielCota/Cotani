package com.cotani.trade.api;

import java.util.Objects;

/** Base exception for expected trade-domain failures. */
public class TradeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TradeException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}

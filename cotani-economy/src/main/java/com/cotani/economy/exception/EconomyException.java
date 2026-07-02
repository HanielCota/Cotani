package com.cotani.economy.exception;

public class EconomyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EconomyException(String message) {
        super(message);
    }

    public EconomyException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.cotani.economy.exception;

import java.io.Serial;

public class EconomyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EconomyException(String message) {
        super(message);
    }

    public EconomyException(String message, Throwable cause) {
        super(message, cause);
    }
}

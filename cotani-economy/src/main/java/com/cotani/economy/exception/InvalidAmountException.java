package com.cotani.economy.exception;

import java.math.BigDecimal;

public final class InvalidAmountException extends EconomyException {

    private static final long serialVersionUID = 1L;

    public InvalidAmountException(BigDecimal amount, String reason) {
        super("Invalid economy amount " + amount + ": " + reason);
    }
}

package com.cotani.economy.exception;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.UUID;

public final class InsufficientFundsException extends EconomyException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(UUID userId, BigDecimal balance, BigDecimal required) {
        super("User " + userId + " has " + balance + ", required " + required + ".");
    }
}

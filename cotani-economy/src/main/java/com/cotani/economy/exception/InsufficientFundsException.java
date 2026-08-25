package com.cotani.economy.exception;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.UUID;

public final class InsufficientFundsException extends EconomyException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(UUID userId, BigDecimal balance, BigDecimal required) {
        super("User " + java.util.Objects.requireNonNull(userId, "userId") + " has "
                + java.util.Objects.requireNonNull(balance, "balance") + ", required "
                + java.util.Objects.requireNonNull(required, "required") + ".");
    }
}

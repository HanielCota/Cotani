package com.cotani.economy.exception;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.UUID;

public final class MaximumBalanceExceededException extends EconomyException {
    @Serial
    private static final long serialVersionUID = 1L;

    public MaximumBalanceExceededException(UUID userId, BigDecimal balance, BigDecimal maximumBalance) {
        super("User " + java.util.Objects.requireNonNull(userId, "userId") + " would exceed maximum balance. Balance: "
                + java.util.Objects.requireNonNull(balance, "balance") + ", maximum: "
                + java.util.Objects.requireNonNull(maximumBalance, "maximumBalance") + ".");
    }
}

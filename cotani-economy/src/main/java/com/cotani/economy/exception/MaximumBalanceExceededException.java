package com.cotani.economy.exception;

import java.math.BigDecimal;
import java.util.UUID;

public final class MaximumBalanceExceededException extends EconomyException {

    private static final long serialVersionUID = 1L;

    public MaximumBalanceExceededException(UUID userId, BigDecimal balance, BigDecimal maximumBalance) {
        super("User " + userId + " would exceed maximum balance. Balance: " + balance + ", maximum: " + maximumBalance
                + ".");
    }
}

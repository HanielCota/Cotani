package com.cotani.economy.exception;

import java.io.Serial;
import java.util.UUID;

public final class SameEconomyAccountTransferException extends EconomyException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SameEconomyAccountTransferException(UUID userId) {
        super("User " + userId + " cannot transfer money to the same economy account.");
    }
}

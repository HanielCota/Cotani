package com.cotani.economy.exception;

import java.util.UUID;

public final class SameEconomyAccountTransferException extends EconomyException {

    private static final long serialVersionUID = 1L;

    public SameEconomyAccountTransferException(UUID userId) {
        super("User " + userId + " cannot transfer money to the same economy account.");
    }
}

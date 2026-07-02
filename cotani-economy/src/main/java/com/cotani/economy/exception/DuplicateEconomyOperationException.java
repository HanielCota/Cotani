package com.cotani.economy.exception;

import com.cotani.economy.transaction.EconomyOperationId;

public final class DuplicateEconomyOperationException extends EconomyException {

    private static final long serialVersionUID = 1L;

    public DuplicateEconomyOperationException(EconomyOperationId operationId) {
        super("Economy operation already exists: " + operationId.value() + ".");
    }
}

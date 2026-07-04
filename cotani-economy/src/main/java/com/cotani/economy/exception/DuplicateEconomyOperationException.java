package com.cotani.economy.exception;

import com.cotani.economy.transaction.EconomyOperationId;
import java.io.Serial;

public final class DuplicateEconomyOperationException extends EconomyException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateEconomyOperationException(EconomyOperationId operationId) {
        super("Economy operation already exists: " + operationId.value() + ".");
    }
}

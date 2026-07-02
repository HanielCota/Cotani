package com.cotani.economy.internal.protection;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyGuard {

    BigDecimal normalizeAmount(BigDecimal amount);

    void validateBalanceAmount(BigDecimal amount);

    void validateUserId(UUID userId);

    void validateCurrencyId(CurrencyId currencyId);

    void validateReason(EconomyReason reason);

    void validateOperationId(EconomyOperationId operationId);

    void validateTransfer(UUID sourceUserId, UUID targetUserId, BigDecimal amount);
}

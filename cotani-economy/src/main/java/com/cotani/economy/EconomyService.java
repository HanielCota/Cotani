package com.cotani.economy;

import com.cotani.economy.account.EconomyBalance;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Public economy API used by other Cotani modules.
 *
 * <p>The API is asynchronous by design. Callers must compose stages instead of blocking with join/get.
 */
public interface EconomyService {

    CompletionStage<EconomyBalance> balance(UUID userId);

    CompletionStage<EconomyBalance> balance(UUID userId, CurrencyId currencyId);

    CompletionStage<Boolean> has(UUID userId, BigDecimal amount);

    CompletionStage<Boolean> has(UUID userId, CurrencyId currencyId, BigDecimal amount);

    CompletionStage<EconomyTransaction> deposit(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> deposit(UUID userId, BigDecimal amount, EconomyReason reason) {
        return deposit(userId, amount, reason, EconomyOperationId.random());
    }

    CompletionStage<EconomyTransaction> withdraw(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> withdraw(UUID userId, BigDecimal amount, EconomyReason reason) {
        return withdraw(userId, amount, reason, EconomyOperationId.random());
    }

    CompletionStage<EconomyTransaction> set(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> set(UUID userId, BigDecimal amount, EconomyReason reason) {
        return set(userId, amount, reason, EconomyOperationId.random());
    }

    CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    default CompletionStage<EconomyTransaction> transfer(
            UUID sourceUserId, UUID targetUserId, BigDecimal amount, EconomyReason reason) {
        return transfer(sourceUserId, targetUserId, amount, reason, EconomyOperationId.random());
    }
}

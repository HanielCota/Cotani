package com.cotani.economy;

import com.cotani.economy.account.EconomyBalance;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public economy API used by other Cotani modules.
 *
 * <p>The API is asynchronous by design. Callers must compose futures instead of blocking with join/get.
 */
public interface EconomyService {

    CompletableFuture<EconomyBalance> balance(UUID userId);

    CompletableFuture<EconomyBalance> balance(UUID userId, CurrencyId currencyId);

    CompletableFuture<Boolean> has(UUID userId, BigDecimal amount);

    CompletableFuture<Boolean> has(UUID userId, CurrencyId currencyId, BigDecimal amount);

    CompletableFuture<EconomyTransaction> deposit(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    CompletableFuture<EconomyTransaction> withdraw(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    CompletableFuture<EconomyTransaction> set(
            UUID userId, BigDecimal amount, EconomyReason reason, EconomyOperationId operationId);

    CompletableFuture<EconomyTransaction> transfer(
            UUID sourceUserId,
            UUID targetUserId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);
}

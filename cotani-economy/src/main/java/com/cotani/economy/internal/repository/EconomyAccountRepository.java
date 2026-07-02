package com.cotani.economy.internal.repository;

import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface EconomyAccountRepository {

    CompletionStage<EconomyAccount> getOrCreate(UUID userId, CurrencyId currencyId);

    CompletionStage<EconomyTransaction> deposit(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> withdraw(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);

    CompletionStage<EconomyTransaction> set(
            UUID userId,
            CurrencyId currencyId,
            BigDecimal amount,
            EconomyReason reason,
            EconomyOperationId operationId);
}

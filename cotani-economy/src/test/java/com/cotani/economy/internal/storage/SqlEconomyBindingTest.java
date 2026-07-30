package com.cotani.economy.internal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cotani.economy.account.EconomyAccount;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionId;
import com.cotani.storage.query.ParameterBinder;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SqlEconomyBindingTest {

    private static final CurrencyId CURRENCY_ID = CurrencyId.of("coins");
    private static final Instant CREATED_AT = Instant.parse("2026-07-29T12:34:56Z");

    @Test
    void accountTimestampsReachTheJdbcBinderAsInstants() throws SQLException {
        var binder = mock(ParameterBinder.class);
        var userId = UUID.randomUUID();
        var account = EconomyAccount.create(userId, CURRENCY_ID, new BigDecimal("10.00"), CREATED_AT);

        SqlEconomyStore.bindAccount(binder, account);

        var values = ArgumentCaptor.forClass(Object.class);
        verify(binder, times(5)).set(values.capture());
        assertEquals(
                Arrays.asList(userId, CURRENCY_ID.value(), "10.00", CREATED_AT, CREATED_AT), values.getAllValues());
    }

    @Test
    void transactionTimestampReachesTheJdbcBinderAsAnInstant() throws SQLException {
        var binder = mock(ParameterBinder.class);
        var transaction = new EconomyTransaction.Deposit(
                EconomyTransactionId.random(),
                EconomyOperationId.random(),
                UUID.randomUUID(),
                CURRENCY_ID,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                EconomyReason.system("test"),
                CREATED_AT);

        EconomyStorageMappers.bindTransaction(binder, transaction);

        var values = ArgumentCaptor.forClass(Object.class);
        verify(binder, times(15)).set(values.capture());
        assertEquals(
                Arrays.asList(
                        transaction.id().value(),
                        transaction.operationId().value(),
                        transaction.type().name(),
                        transaction.sourceUserId(),
                        transaction.targetUserId(),
                        transaction.currencyId().value(),
                        transaction.amount().toPlainString(),
                        null,
                        null,
                        transaction.targetBalanceBefore().toPlainString(),
                        transaction.targetBalanceAfter().toPlainString(),
                        transaction.reason().key(),
                        transaction.reason().source(),
                        transaction.reason().actorUserId(),
                        CREATED_AT),
                values.getAllValues());
    }
}

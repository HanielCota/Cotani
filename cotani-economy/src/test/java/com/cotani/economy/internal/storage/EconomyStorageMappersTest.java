package com.cotani.economy.internal.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionType;
import com.cotani.storage.error.QueryError;
import com.cotani.storage.error.StorageException;
import com.cotani.storage.query.Row;
import com.cotani.storage.serializer.ValueSerializerRegistry;
import com.cotani.storage.transaction.TransactionContext;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NullAway")
class EconomyStorageMappersTest {
    private static final CurrencyId CURRENCY = CurrencyId.of("coins");
    private static final Instant NOW = Instant.parse("2026-07-29T12:34:56Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @Test
    void shouldMapAccountRowToEconomyAccount() throws Exception {
        var row = rowWith("balance", "42.50", "created_at", NOW, "updated_at", NOW.plusSeconds(5));

        var account = EconomyStorageMappers.accountFromRow(USER_ID, CURRENCY, row);

        assertEquals(USER_ID, account.userId());
        assertEquals(CURRENCY, account.currencyId());
        assertEquals(0, account.balance().compareTo(new BigDecimal("42.50")));
        assertEquals(NOW, account.createdAt());
        assertEquals(NOW.plusSeconds(5), account.updatedAt());
    }

    @Test
    void shouldMapDepositRowToDepositTransaction() throws Exception {
        var transaction = EconomyStorageMappers.transactionFromRow(rowWith(
                "transaction_id", UUID.randomUUID(),
                "operation_id", UUID.randomUUID(),
                "type", "DEPOSIT",
                "currency_id", "coins",
                "amount", "10.00",
                "reason_key", "reward",
                "reason_source", "cotani",
                "created_at", NOW,
                "target_user_id", USER_ID,
                "target_balance_before", "0.00",
                "target_balance_after", "10.00"));

        assertInstanceOf(EconomyTransaction.Deposit.class, transaction);
        assertEquals(EconomyTransactionType.DEPOSIT, transaction.type());
        assertEquals(USER_ID, transaction.target().orElseThrow());
        assertEquals(0, transaction.amount().compareTo(new BigDecimal("10.00")));
        assertEquals("reward", transaction.reason().key());
        assertEquals("cotani", transaction.reason().source());
        assertTrue(transaction.reason().actor().isEmpty());
        assertEquals(NOW, transaction.createdAt());
    }

    @Test
    void shouldMapWithdrawRowToWithdrawTransaction() throws Exception {
        var transaction = EconomyStorageMappers.transactionFromRow(rowWith(
                "transaction_id", UUID.randomUUID(),
                "operation_id", UUID.randomUUID(),
                "type", "WITHDRAW",
                "currency_id", "coins",
                "amount", "3.50",
                "reason_key", "purchase",
                "reason_source", "shop",
                "created_at", NOW,
                "source_user_id", USER_ID,
                "source_balance_before", "10.00",
                "source_balance_after", "6.50"));

        assertInstanceOf(EconomyTransaction.Withdraw.class, transaction);
        assertEquals(USER_ID, transaction.source().orElseThrow());
        assertEquals(0, transaction.amount().compareTo(new BigDecimal("3.50")));
    }

    @Test
    void shouldMapSetRowToSetTransaction() throws Exception {
        var transaction = EconomyStorageMappers.transactionFromRow(rowWith(
                "transaction_id", UUID.randomUUID(),
                "operation_id", UUID.randomUUID(),
                "type", "SET",
                "currency_id", "coins",
                "amount", "77.00",
                "reason_key", "admin",
                "reason_source", "cotani",
                "created_at", NOW,
                "target_user_id", USER_ID,
                "target_balance_before", "10.00",
                "target_balance_after", "77.00"));

        assertInstanceOf(EconomyTransaction.Set.class, transaction);
        assertEquals(EconomyTransactionType.SET, transaction.type());
        assertEquals(USER_ID, transaction.target().orElseThrow());
    }

    @Test
    void shouldMapTransferRowToTransferTransaction() throws Exception {
        var transaction = EconomyStorageMappers.transactionFromRow(rowWith(
                "transaction_id", UUID.randomUUID(),
                "operation_id", UUID.randomUUID(),
                "type", "TRANSFER",
                "currency_id", "coins",
                "amount", "5.00",
                "reason_key", "tip",
                "reason_source", "player",
                "reason_actor_user_id", OTHER_USER_ID,
                "created_at", NOW,
                "source_user_id", USER_ID,
                "target_user_id", OTHER_USER_ID,
                "source_balance_before", "10.00",
                "source_balance_after", "5.00",
                "target_balance_before", "0.00",
                "target_balance_after", "5.00"));

        assertInstanceOf(EconomyTransaction.Transfer.class, transaction);
        assertEquals(USER_ID, transaction.source().orElseThrow());
        assertEquals(OTHER_USER_ID, transaction.target().orElseThrow());
        assertTrue(transaction.reason().actor().isPresent());
        assertEquals(OTHER_USER_ID, transaction.reason().actor().orElseThrow());
        assertEquals(0, transaction.sourceBalanceAfter().compareTo(new BigDecimal("5.00")));
    }

    @Test
    void shouldFailMappingWhenRequiredUuidColumnIsNull() {
        var row = rowWith(
                "transaction_id", UUID.randomUUID(),
                "operation_id", UUID.randomUUID(),
                "type", "DEPOSIT",
                "currency_id", "coins",
                "amount", "10.00",
                "reason_key", "reward",
                "reason_source", "cotani",
                "created_at", NOW,
                "target_user_id", (Object) null,
                "target_balance_before", "0.00",
                "target_balance_after", "10.00");

        var failure = assertThrows(IllegalStateException.class, () -> EconomyStorageMappers.transactionFromRow(row));
        assertTrue(failure.getMessage().contains("target_user_id"));
    }

    @Test
    void shouldFailMappingWhenRequiredTimestampColumnIsNull() {
        var row = rowWith("balance", "10.00", "created_at", (Object) null, "updated_at", NOW);

        var failure = assertThrows(
                IllegalStateException.class, () -> EconomyStorageMappers.accountFromRow(USER_ID, CURRENCY, row));
        assertTrue(failure.getMessage().contains("created_at"));
    }

    @Test
    void shouldConvertUniqueViolationIntoDuplicateOperationException() throws Exception {
        var transaction = EconomyTransaction.deposit(
                EconomyOperationId.random(),
                USER_ID,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                EconomyReason.system("test"),
                NOW);
        var tx = mock(TransactionContext.class);
        var uniqueViolation = new SQLException("UNIQUE constraint failed", "23505");
        when(tx.update(anyString(), any())).thenReturn(CompletableFuture.failedFuture(uniqueViolation));

        var stage = EconomyStorageMappers.insertTransaction(tx, transaction).toCompletableFuture();

        var failure = assertThrows(ExecutionException.class, () -> stage.get(5, TimeUnit.SECONDS));
        assertInstanceOf(DuplicateEconomyOperationException.class, failure.getCause());
    }

    @Test
    void shouldPropagateNonUniqueFailuresAsIs() throws Exception {
        var transaction = EconomyTransaction.deposit(
                EconomyOperationId.random(),
                USER_ID,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                EconomyReason.system("test"),
                NOW);
        var tx = mock(TransactionContext.class);
        var storageFailure = new IllegalStateException("database down");
        when(tx.update(anyString(), any())).thenReturn(CompletableFuture.failedFuture(storageFailure));

        var stage = EconomyStorageMappers.insertTransaction(tx, transaction).toCompletableFuture();

        var failure = assertThrows(ExecutionException.class, () -> stage.get(5, TimeUnit.SECONDS));
        assertEquals(storageFailure, failure.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "23000"})
    void shouldDetectUniqueViolationBySqlState(String sqlState) {
        assertTrue(EconomyStorageMappers.isUniqueViolation(new SQLException("constraint failed", sqlState)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1062, 19})
    void shouldDetectUniqueViolationByErrorCode(int errorCode) {
        assertTrue(EconomyStorageMappers.isUniqueViolation(new SQLException("constraint", null, errorCode)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNIQUE constraint failed: table.x", "Duplicate entry '1' for key 'operation_id'"})
    void shouldDetectUniqueViolationByMessage(String message) {
        assertTrue(EconomyStorageMappers.isUniqueViolation(new SQLException(message)));
    }

    @Test
    void shouldDetectUniqueViolationWrappedInStorageException() {
        var wrapped = new StorageException(new QueryError("could not update", new SQLException("duplicate", "23505")));

        assertTrue(EconomyStorageMappers.isUniqueViolation(wrapped));
    }

    @Test
    void shouldDetectUniqueViolationNestedInCompletionExceptions() {
        var sqlFailure = new SQLException("UNIQUE constraint failed", "23505");
        var nested = new CompletionException(
                new CompletionException(new StorageException(new QueryError("boom", sqlFailure))));

        assertTrue(EconomyStorageMappers.isUniqueViolation(nested));
    }

    @Test
    void shouldNotTreatUnrelatedFailuresAsUniqueViolations() {
        var sqlFailure = new SQLException("syntax error", "42000");

        assertFalse(EconomyStorageMappers.isUniqueViolation(sqlFailure));
        assertFalse(EconomyStorageMappers.isUniqueViolation(new RuntimeException(sqlFailure)));
        assertFalse(EconomyStorageMappers.isUniqueViolation(new StorageException(new QueryError("boom", sqlFailure))));
        assertFalse(EconomyStorageMappers.isUniqueViolation(new SQLException((String) null)));
    }

    private static Row rowWith(Object... columnValuePairs) {
        var resultSet = mock(ResultSet.class);
        var serializers = mock(ValueSerializerRegistry.class);

        try {
            for (int i = 0; i < columnValuePairs.length; i += 2) {
                var column = (String) columnValuePairs[i];
                var value = columnValuePairs[i + 1];

                if (value == null) {
                    when(resultSet.getString(column)).thenReturn(null);
                } else if (value instanceof Instant instant) {
                    when(resultSet.getObject(column)).thenReturn(Timestamp.from(instant));
                } else if (value instanceof UUID uuid) {
                    when(resultSet.getString(column)).thenReturn(uuid.toString());
                } else {
                    when(resultSet.getString(column)).thenReturn(value.toString());
                }
            }
        } catch (SQLException impossible) {
            throw new AssertionError(impossible);
        }

        return new Row(resultSet, serializers);
    }
}

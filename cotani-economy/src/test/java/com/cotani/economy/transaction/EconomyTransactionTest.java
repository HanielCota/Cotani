package com.cotani.economy.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyTransactionTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final CurrencyId CURRENCY = CurrencyId.of("coins");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final EconomyOperationId OPERATION_ID = EconomyOperationId.random();
    private static final EconomyReason REASON = EconomyReason.system("test");

    @Test
    void shouldCreateDepositWithAllFields() {
        var transaction = EconomyTransaction.deposit(
                OPERATION_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, REASON, NOW);

        assertEquals(EconomyTransactionType.DEPOSIT, transaction.type());
        assertNotNull(transaction.id());
        assertEquals(OPERATION_ID, transaction.operationId());
        assertEquals(CURRENCY, transaction.currencyId());
        assertEquals(0, transaction.amount().compareTo(BigDecimal.TEN));
        assertEquals(REASON, transaction.reason());
        assertEquals(NOW, transaction.createdAt());
        assertTrue(transaction.source().isEmpty());
        assertTrue(transaction.target().isPresent());
        assertEquals(USER_ID, transaction.target().orElseThrow());
        assertEquals(0, transaction.targetBalanceBefore().compareTo(BigDecimal.ZERO));
        assertEquals(0, transaction.targetBalanceAfter().compareTo(BigDecimal.TEN));
        assertTrue(transaction.optionalSourceBalanceBefore().isEmpty());
        assertTrue(transaction.optionalSourceBalanceAfter().isEmpty());
    }

    @Test
    void shouldCreateWithdrawWithAllFields() {
        var transaction = EconomyTransaction.withdraw(
                OPERATION_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, REASON, NOW);

        assertEquals(EconomyTransactionType.WITHDRAW, transaction.type());
        assertTrue(transaction.target().isEmpty());
        assertTrue(transaction.source().isPresent());
        assertEquals(USER_ID, transaction.source().orElseThrow());
        assertEquals(0, transaction.sourceBalanceBefore().compareTo(BigDecimal.TEN));
        assertEquals(0, transaction.sourceBalanceAfter().compareTo(BigDecimal.ZERO));
        assertTrue(transaction.optionalTargetBalanceBefore().isEmpty());
        assertTrue(transaction.optionalTargetBalanceAfter().isEmpty());
    }

    @Test
    void shouldCreateSetTransactionWithAllFields() {
        var transaction = EconomyTransaction.set(
                OPERATION_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, REASON, NOW);

        assertEquals(EconomyTransactionType.SET, transaction.type());
        assertTrue(transaction.source().isEmpty());
        assertEquals(USER_ID, transaction.target().orElseThrow());
        assertEquals(0, transaction.amount().compareTo(BigDecimal.TEN));
    }

    @Test
    void shouldCreateTransferWithAllFields() {
        var transaction = EconomyTransaction.transfer(
                OPERATION_ID,
                USER_ID,
                OTHER_USER_ID,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.valueOf(20),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                REASON,
                NOW);

        assertEquals(EconomyTransactionType.TRANSFER, transaction.type());
        assertEquals(USER_ID, transaction.source().orElseThrow());
        assertEquals(OTHER_USER_ID, transaction.target().orElseThrow());
        assertEquals(0, transaction.sourceBalanceBefore().compareTo(BigDecimal.valueOf(20)));
        assertEquals(0, transaction.sourceBalanceAfter().compareTo(BigDecimal.TEN));
        assertEquals(0, transaction.targetBalanceBefore().compareTo(BigDecimal.ZERO));
        assertEquals(0, transaction.targetBalanceAfter().compareTo(BigDecimal.TEN));
        assertTrue(transaction.optionalSourceBalanceBefore().isPresent());
        assertTrue(transaction.optionalTargetBalanceAfter().isPresent());
    }

    @Test
    void shouldGenerateUniqueTransactionIdsPerFactoryCall() {
        var first = EconomyTransaction.deposit(
                OPERATION_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, REASON, NOW);
        var second = EconomyTransaction.deposit(
                OPERATION_ID, USER_ID, CURRENCY, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, REASON, NOW);

        assertNotEquals(first.id(), second.id());
    }

    @Test
    void shouldRejectNonPositiveAmountForDepositWithdrawAndTransfer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.deposit(
                        OPERATION_ID,
                        USER_ID,
                        CURRENCY,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.deposit(
                        OPERATION_ID,
                        USER_ID,
                        CURRENCY,
                        BigDecimal.valueOf(-1),
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.withdraw(
                        OPERATION_ID, USER_ID, CURRENCY, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, REASON, NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.transfer(
                        OPERATION_ID,
                        USER_ID,
                        OTHER_USER_ID,
                        CURRENCY,
                        BigDecimal.valueOf(-5),
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        REASON,
                        NOW));
    }

    @Test
    void shouldAllowZeroAmountForSetButRejectNegative() {
        var zero = EconomyTransaction.set(
                OPERATION_ID, USER_ID, CURRENCY, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, REASON, NOW);

        assertEquals(0, zero.amount().compareTo(BigDecimal.ZERO));

        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.set(
                        OPERATION_ID,
                        USER_ID,
                        CURRENCY,
                        BigDecimal.valueOf(-1),
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFieldsOnDeposit() {
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(
                        null,
                        OPERATION_ID,
                        USER_ID,
                        CURRENCY,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(
                        EconomyTransactionId.random(),
                        null,
                        USER_ID,
                        CURRENCY,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(
                        EconomyTransactionId.random(),
                        OPERATION_ID,
                        null,
                        CURRENCY,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(
                        EconomyTransactionId.random(),
                        OPERATION_ID,
                        USER_ID,
                        null,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(
                        EconomyTransactionId.random(),
                        OPERATION_ID,
                        USER_ID,
                        CURRENCY,
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(
                        EconomyTransactionId.random(),
                        OPERATION_ID,
                        USER_ID,
                        CURRENCY,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        null,
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(
                        EconomyTransactionId.random(),
                        OPERATION_ID,
                        USER_ID,
                        CURRENCY,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFieldsOnTransfer() {
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Transfer(
                        null,
                        OPERATION_ID,
                        USER_ID,
                        OTHER_USER_ID,
                        CURRENCY,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Transfer(
                        EconomyTransactionId.random(),
                        OPERATION_ID,
                        USER_ID,
                        null,
                        CURRENCY,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.TEN,
                        REASON,
                        NOW));
    }

    @Test
    void shouldImplementValueEquality() {
        var transactionId = EconomyTransactionId.random();
        var first = new EconomyTransaction.Deposit(
                transactionId,
                OPERATION_ID,
                USER_ID,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                REASON,
                NOW);
        var second = new EconomyTransaction.Deposit(
                transactionId,
                OPERATION_ID,
                USER_ID,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                REASON,
                NOW);
        var differentAmount = new EconomyTransaction.Deposit(
                transactionId,
                OPERATION_ID,
                USER_ID,
                CURRENCY,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                REASON,
                NOW);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, differentAmount);
        assertNotEquals(null, first);
    }
}

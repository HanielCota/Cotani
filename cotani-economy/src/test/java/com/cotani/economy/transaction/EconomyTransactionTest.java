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
        var transaction =
                EconomyTransaction.deposit(details(BigDecimal.TEN), change(USER_ID, BigDecimal.ZERO, BigDecimal.TEN));

        assertEquals(EconomyTransactionType.DEPOSIT, transaction.type());
        assertNotNull(transaction.id());
        assertEquals(OPERATION_ID, transaction.operationId());
        assertEquals(CURRENCY, transaction.currencyId());
        assertEquals(0, transaction.amount().compareTo(BigDecimal.TEN));
        assertEquals(REASON, transaction.reason());
        assertEquals(NOW, transaction.createdAt());
        assertTrue(transaction.source().isEmpty());
        assertEquals(USER_ID, transaction.target().orElseThrow());
        assertEquals(0, transaction.targetBalanceBefore().compareTo(BigDecimal.ZERO));
        assertEquals(0, transaction.targetBalanceAfter().compareTo(BigDecimal.TEN));
        assertTrue(transaction.optionalSourceBalanceBefore().isEmpty());
        assertTrue(transaction.optionalSourceBalanceAfter().isEmpty());
    }

    @Test
    void shouldCreateWithdrawWithAllFields() {
        var transaction =
                EconomyTransaction.withdraw(details(BigDecimal.TEN), change(USER_ID, BigDecimal.TEN, BigDecimal.ZERO));

        assertEquals(EconomyTransactionType.WITHDRAW, transaction.type());
        assertTrue(transaction.target().isEmpty());
        assertEquals(USER_ID, transaction.source().orElseThrow());
        assertEquals(0, transaction.sourceBalanceBefore().compareTo(BigDecimal.TEN));
        assertEquals(0, transaction.sourceBalanceAfter().compareTo(BigDecimal.ZERO));
        assertTrue(transaction.optionalTargetBalanceBefore().isEmpty());
        assertTrue(transaction.optionalTargetBalanceAfter().isEmpty());
    }

    @Test
    void shouldCreateSetTransactionWithAllFields() {
        var transaction =
                EconomyTransaction.set(details(BigDecimal.TEN), change(USER_ID, BigDecimal.ZERO, BigDecimal.TEN));

        assertEquals(EconomyTransactionType.SET, transaction.type());
        assertTrue(transaction.source().isEmpty());
        assertEquals(USER_ID, transaction.target().orElseThrow());
        assertEquals(0, transaction.amount().compareTo(BigDecimal.TEN));
    }

    @Test
    void shouldCreateTransferWithAllFields() {
        var transaction = EconomyTransaction.transfer(
                details(BigDecimal.TEN),
                change(USER_ID, BigDecimal.valueOf(20), BigDecimal.TEN),
                change(OTHER_USER_ID, BigDecimal.ZERO, BigDecimal.TEN));

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
        var first =
                EconomyTransaction.deposit(details(BigDecimal.TEN), change(USER_ID, BigDecimal.ZERO, BigDecimal.TEN));
        var second =
                EconomyTransaction.deposit(details(BigDecimal.TEN), change(USER_ID, BigDecimal.ZERO, BigDecimal.TEN));

        assertNotEquals(first.id(), second.id());
    }

    @Test
    void shouldRejectNonPositiveAmountForDepositWithdrawAndTransfer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.deposit(
                        details(BigDecimal.ZERO), change(USER_ID, BigDecimal.ZERO, BigDecimal.TEN)));
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.withdraw(
                        details(BigDecimal.ZERO), change(USER_ID, BigDecimal.TEN, BigDecimal.TEN)));
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.transfer(
                        details(BigDecimal.valueOf(-5)),
                        change(USER_ID, BigDecimal.TEN, BigDecimal.TEN),
                        change(OTHER_USER_ID, BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    @Test
    void shouldAllowZeroAmountForSetButRejectNegative() {
        var zero = EconomyTransaction.set(details(BigDecimal.ZERO), change(USER_ID, BigDecimal.TEN, BigDecimal.TEN));

        assertEquals(0, zero.amount().compareTo(BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyTransaction.set(
                        details(BigDecimal.valueOf(-1)), change(USER_ID, BigDecimal.TEN, BigDecimal.TEN)));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFieldsOnDeposit() {
        var details = details(BigDecimal.TEN);
        var target = change(USER_ID, BigDecimal.ZERO, BigDecimal.TEN);

        assertThrows(NullPointerException.class, () -> new EconomyTransaction.Deposit(null, details, target));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(EconomyTransactionId.random(), null, target));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Deposit(EconomyTransactionId.random(), details, null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFieldsOnTransfer() {
        var details = details(BigDecimal.TEN);
        var source = change(USER_ID, BigDecimal.TEN, BigDecimal.ZERO);
        var target = change(OTHER_USER_ID, BigDecimal.ZERO, BigDecimal.TEN);

        assertThrows(NullPointerException.class, () -> new EconomyTransaction.Transfer(null, details, source, target));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Transfer(EconomyTransactionId.random(), null, source, target));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Transfer(EconomyTransactionId.random(), details, null, target));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyTransaction.Transfer(EconomyTransactionId.random(), details, source, null));
    }

    @Test
    void shouldImplementValueEquality() {
        var transactionId = EconomyTransactionId.random();
        var details = details(BigDecimal.TEN);
        var target = change(USER_ID, BigDecimal.ZERO, BigDecimal.TEN);
        var first = new EconomyTransaction.Deposit(transactionId, details, target);
        var second = new EconomyTransaction.Deposit(transactionId, details, target);
        var differentAmount = new EconomyTransaction.Deposit(
                transactionId, details(BigDecimal.ONE), change(USER_ID, BigDecimal.ZERO, BigDecimal.ONE));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, differentAmount);
        assertNotEquals(null, first);
    }

    private static EconomyTransactionDetails details(BigDecimal amount) {
        return new EconomyTransactionDetails(OPERATION_ID, CURRENCY, amount, REASON, NOW);
    }

    private static EconomyBalanceChange change(UUID userId, BigDecimal before, BigDecimal after) {
        return new EconomyBalanceChange(userId, before, after);
    }
}

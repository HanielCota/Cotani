package com.cotani.economy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.DuplicateEconomyOperationException;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionId;
import com.cotani.economy.transaction.EconomyTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyOperationFingerprintTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final CurrencyId CURRENCY = CurrencyId.of("coins");
    private static final EconomyReason REASON = EconomyReason.system("test");

    @Test
    void shouldCreateDepositFingerprint() {
        var fingerprint = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON);

        assertEquals(EconomyTransactionType.DEPOSIT, fingerprint.type());
        assertNull(fingerprint.sourceUserId());
        assertEquals(USER_ID, fingerprint.targetUserId());
        assertEquals(CURRENCY, fingerprint.currencyId());
        assertEquals(0, fingerprint.amount().compareTo(BigDecimal.TEN));
        assertEquals(REASON, fingerprint.reason());
    }

    @Test
    void shouldCreateWithdrawFingerprint() {
        var fingerprint = EconomyOperationFingerprint.withdraw(USER_ID, CURRENCY, BigDecimal.TEN, REASON);

        assertEquals(EconomyTransactionType.WITHDRAW, fingerprint.type());
        assertEquals(USER_ID, fingerprint.sourceUserId());
        assertNull(fingerprint.targetUserId());
    }

    @Test
    void shouldCreateSetFingerprint() {
        var fingerprint = EconomyOperationFingerprint.set(USER_ID, CURRENCY, BigDecimal.TEN, REASON);

        assertEquals(EconomyTransactionType.SET, fingerprint.type());
        assertEquals(USER_ID, fingerprint.targetUserId());
    }

    @Test
    void shouldCreateTransferFingerprint() {
        var fingerprint =
                EconomyOperationFingerprint.transfer(USER_ID, OTHER_USER_ID, CURRENCY, BigDecimal.TEN, REASON);

        assertEquals(EconomyTransactionType.TRANSFER, fingerprint.type());
        assertEquals(USER_ID, fingerprint.sourceUserId());
        assertEquals(OTHER_USER_ID, fingerprint.targetUserId());
    }

    @Test
    void shouldMatchSameRequestIgnoringAmountScale() {
        var first = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, new BigDecimal("1.0"), REASON);
        var second = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, new BigDecimal("1.00"), REASON);

        assertTrue(first.sameRequest(second));
    }

    @Test
    void shouldNotMatchDifferentAmount() {
        var first = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON);
        var second = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.ONE, REASON);

        assertFalse(first.sameRequest(second));
    }

    @Test
    void shouldNotMatchDifferentTypeSourceTargetCurrencyOrReason() {
        var base = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON);

        assertFalse(base.sameRequest(EconomyOperationFingerprint.withdraw(USER_ID, CURRENCY, BigDecimal.TEN, REASON)));
        assertFalse(
                base.sameRequest(EconomyOperationFingerprint.deposit(OTHER_USER_ID, CURRENCY, BigDecimal.TEN, REASON)));
        assertFalse(base.sameRequest(
                EconomyOperationFingerprint.deposit(USER_ID, CurrencyId.of("gems"), BigDecimal.TEN, REASON)));
        assertFalse(base.sameRequest(
                EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, EconomyReason.system("other"))));
    }

    @Test
    void shouldReturnExistingTransactionWhenRequestMatches() {
        var operationId = EconomyOperationId.random();
        var fingerprint = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON);
        var transaction = new EconomyTransaction.Deposit(
                EconomyTransactionId.random(),
                operationId,
                USER_ID,
                CURRENCY,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                REASON,
                Instant.now());

        assertSame(transaction, fingerprint.requireMatch(operationId, transaction));
    }

    @Test
    void shouldRejectExistingTransactionWhenRequestDoesNotMatch() {
        var operationId = EconomyOperationId.random();
        var fingerprint = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON);
        var conflicting = new EconomyTransaction.Deposit(
                EconomyTransactionId.random(),
                operationId,
                USER_ID,
                CURRENCY,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                REASON,
                Instant.now());

        assertThrows(
                DuplicateEconomyOperationException.class, () -> fingerprint.requireMatch(operationId, conflicting));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFields() {
        assertThrows(
                NullPointerException.class,
                () -> new EconomyOperationFingerprint(null, USER_ID, USER_ID, CURRENCY, BigDecimal.TEN, REASON));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyOperationFingerprint(
                        EconomyTransactionType.DEPOSIT, null, USER_ID, null, BigDecimal.TEN, REASON));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyOperationFingerprint(
                        EconomyTransactionType.DEPOSIT, null, USER_ID, CURRENCY, null, REASON));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyOperationFingerprint(
                        EconomyTransactionType.DEPOSIT, null, USER_ID, CURRENCY, BigDecimal.TEN, null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullInSameRequestAndRequireMatch() {
        var fingerprint = EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON);

        assertThrows(NullPointerException.class, () -> fingerprint.sameRequest(null));
        assertThrows(
                NullPointerException.class,
                () -> fingerprint.requireMatch(
                        null,
                        new EconomyTransaction.Deposit(
                                EconomyTransactionId.random(),
                                EconomyOperationId.random(),
                                USER_ID,
                                CURRENCY,
                                BigDecimal.TEN,
                                BigDecimal.ZERO,
                                BigDecimal.TEN,
                                REASON,
                                Instant.now())));
        assertThrows(NullPointerException.class, () -> fingerprint.requireMatch(EconomyOperationId.random(), null));
    }

    @Test
    void shouldImplementValueEquality() {
        assertEquals(
                EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON),
                EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON));
        assertNotEquals(
                EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.TEN, REASON),
                EconomyOperationFingerprint.deposit(USER_ID, CURRENCY, BigDecimal.ONE, REASON));
    }
}

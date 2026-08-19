package com.cotani.economy.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.exception.InsufficientFundsException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyAccountTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrencyId CURRENCY = CurrencyId.of("coins");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createInitializesBalanceAndTimestamps() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        assertEquals(USER_ID, account.userId());
        assertEquals(CURRENCY, account.currencyId());
        assertEquals(0, account.balance().compareTo(BigDecimal.TEN));
        assertEquals(NOW, account.createdAt());
        assertEquals(NOW, account.updatedAt());
    }

    @Test
    void cannotCreateWithNegativeBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.valueOf(-1), NOW));
    }

    @Test
    void depositIncreasesBalanceAndUpdatesTimestamp() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);
        var later = NOW.plusSeconds(10);

        var updated = account.deposit(BigDecimal.valueOf(5), later);

        assertEquals(0, updated.balance().compareTo(BigDecimal.valueOf(15)));
        assertEquals(NOW, updated.createdAt());
        assertEquals(later, updated.updatedAt());
    }

    @Test
    void depositRequiresPositiveAmount() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        assertThrows(IllegalArgumentException.class, () -> account.deposit(BigDecimal.ZERO, NOW));
        assertThrows(IllegalArgumentException.class, () -> account.deposit(BigDecimal.valueOf(-1), NOW));
    }

    @Test
    void withdrawDecreasesBalance() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        var updated = account.withdraw(BigDecimal.valueOf(3), NOW.plusSeconds(1));

        assertEquals(0, updated.balance().compareTo(BigDecimal.valueOf(7)));
    }

    @Test
    void withdrawFailsWhenBalanceIsInsufficient() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        assertThrows(InsufficientFundsException.class, () -> account.withdraw(BigDecimal.valueOf(11), NOW));
    }

    @Test
    void withdrawRequiresPositiveAmount() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        assertThrows(IllegalArgumentException.class, () -> account.withdraw(BigDecimal.ZERO, NOW));
    }

    @Test
    void setBalanceUpdatesValue() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        var updated = account.setBalance(BigDecimal.valueOf(99), NOW.plusSeconds(1));

        assertEquals(0, updated.balance().compareTo(BigDecimal.valueOf(99)));
    }

    @Test
    void setBalanceRejectsNegativeValue() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        assertThrows(IllegalArgumentException.class, () -> account.setBalance(BigDecimal.valueOf(-1), NOW));
    }

    @Test
    void shouldWithdrawDownToZeroBalance() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        var updated = account.withdraw(BigDecimal.TEN, NOW.plusSeconds(1));

        assertEquals(0, updated.balance().signum());
        assertEquals(0, updated.balance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldSetBalanceToZero() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        var updated = account.setBalance(BigDecimal.ZERO, NOW.plusSeconds(1));

        assertEquals(0, updated.balance().signum());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        assertThrows(NullPointerException.class, () -> account.deposit(null, NOW));
        assertThrows(NullPointerException.class, () -> account.withdraw(null, NOW));
        assertThrows(NullPointerException.class, () -> account.setBalance(null, NOW));
        assertThrows(NullPointerException.class, () -> account.deposit(BigDecimal.ONE, null));
        assertThrows(NullPointerException.class, () -> new EconomyAccount(null, CURRENCY, BigDecimal.TEN, NOW, NOW));
        assertThrows(NullPointerException.class, () -> new EconomyAccount(USER_ID, null, BigDecimal.TEN, NOW, NOW));
        assertThrows(NullPointerException.class, () -> new EconomyAccount(USER_ID, CURRENCY, null, NOW, NOW));
        assertThrows(
                NullPointerException.class, () -> new EconomyAccount(USER_ID, CURRENCY, BigDecimal.TEN, null, NOW));
        assertThrows(
                NullPointerException.class, () -> new EconomyAccount(USER_ID, CURRENCY, BigDecimal.TEN, NOW, null));
        assertThrows(NullPointerException.class, () -> EconomyAccount.create(null, CURRENCY, BigDecimal.TEN, NOW));
        assertThrows(NullPointerException.class, () -> EconomyAccount.create(USER_ID, null, BigDecimal.TEN, NOW));
        assertThrows(NullPointerException.class, () -> EconomyAccount.create(USER_ID, CURRENCY, null, NOW));
        assertThrows(NullPointerException.class, () -> EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, null));
    }

    @Test
    void shouldImplementValueEquality() {
        var first = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);
        var second = EconomyAccount.create(USER_ID, CURRENCY, BigDecimal.TEN, NOW);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, null);
        assertNotEquals(first, "account");
        assertNotEquals(first, first.deposit(BigDecimal.ONE, NOW));
    }
}

package com.cotani.economy.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.economy.currency.CurrencyId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyBalanceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrencyId CURRENCY = CurrencyId.of("coins");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void shouldExposeAllFields() {
        var balance = new EconomyBalance(USER_ID, CURRENCY, new BigDecimal("42.50"));

        assertEquals(USER_ID, balance.userId());
        assertEquals(CURRENCY, balance.currencyId());
        assertEquals(0, balance.amount().compareTo(new BigDecimal("42.50")));
    }

    @Test
    void shouldBuildBalanceFromAccount() {
        var account = EconomyAccount.create(USER_ID, CURRENCY, new BigDecimal("7.25"), NOW);

        var balance = EconomyBalance.from(account);

        assertEquals(account.userId(), balance.userId());
        assertEquals(account.currencyId(), balance.currencyId());
        assertEquals(0, balance.amount().compareTo(account.balance()));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class, () -> new EconomyBalance(USER_ID, CURRENCY, BigDecimal.valueOf(-1)));
    }

    @Test
    void shouldAcceptZeroAmount() {
        assertEquals(
                0,
                new EconomyBalance(USER_ID, CURRENCY, BigDecimal.ZERO).amount().signum());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFields() {
        assertThrows(NullPointerException.class, () -> new EconomyBalance(null, CURRENCY, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new EconomyBalance(USER_ID, null, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> new EconomyBalance(USER_ID, CURRENCY, null));
        assertThrows(NullPointerException.class, () -> EconomyBalance.from(null));
    }

    @Test
    void shouldImplementValueEquality() {
        assertEquals(
                new EconomyBalance(USER_ID, CURRENCY, new BigDecimal("1.00")),
                new EconomyBalance(USER_ID, CURRENCY, new BigDecimal("1.00")));
        assertNotEquals(
                new EconomyBalance(USER_ID, CURRENCY, new BigDecimal("1.00")),
                new EconomyBalance(USER_ID, CURRENCY, new BigDecimal("2.00")));
    }
}

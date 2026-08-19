package com.cotani.economy.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CurrencyIdTest {

    @Test
    void shouldNormalizeToLowerCaseAndTrim() {
        assertEquals(CurrencyId.of("coins"), CurrencyId.of("  Coins  "));
        assertEquals("coins", CurrencyId.of("COINS").value());
    }

    @Test
    void shouldAcceptUnderscoresDashesAndDigits() {
        assertEquals("vip_coins-2", CurrencyId.of("vip_coins-2").value());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullValue() {
        assertThrows(NullPointerException.class, () -> CurrencyId.of(null));
    }

    @Test
    void shouldRejectValuesShorterThanTwoCharacters() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyId.of("a"));
    }

    @Test
    void shouldRejectValuesLongerThanThirtyTwoCharacters() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyId.of("a".repeat(33)));
    }

    @Test
    void shouldRejectDisallowedCharacters() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyId.of("coin$"));
        assertThrows(IllegalArgumentException.class, () -> CurrencyId.of("with space"));
        assertThrows(IllegalArgumentException.class, () -> CurrencyId.of("coin."));
    }

    @Test
    void shouldAcceptMaximumAllowedLength() {
        assertEquals("a".repeat(32), CurrencyId.of("a".repeat(32)).value());
    }

    @Test
    void shouldImplementValueEquality() {
        assertEquals(CurrencyId.of("coins"), CurrencyId.of("coins"));
        assertNotEquals(CurrencyId.of("coins"), CurrencyId.of("gems"));
    }
}

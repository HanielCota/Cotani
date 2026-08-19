package com.cotani.economy.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EconomyCurrencyTest {

    @Test
    void shouldCreateCoinsCurrencyFactory() {
        var currency = EconomyCurrency.coins();

        assertEquals(CurrencyId.of("coins"), currency.id());
        assertEquals("Coins", currency.name());
        assertEquals("$", currency.symbol());
        assertEquals(2, currency.decimalPlaces());
    }

    @Test
    void shouldTrimNameAndSymbol() {
        var currency = new EconomyCurrency(CurrencyId.of("gems"), "  Gems  ", "  G  ", 0);

        assertEquals("Gems", currency.name());
        assertEquals("G", currency.symbol());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyCurrency(CurrencyId.of("gems"), "   ", "G", 0));
        assertThrows(IllegalArgumentException.class, () -> new EconomyCurrency(CurrencyId.of("gems"), "", "G", 0));
    }

    @Test
    void shouldRejectBlankSymbol() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyCurrency(CurrencyId.of("gems"), "Gems", " ", 0));
    }

    @Test
    void shouldAcceptDecimalPlacesBoundariesZeroAndEight() {
        assertEquals(0, new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 0).decimalPlaces());
        assertEquals(8, new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 8).decimalPlaces());
    }

    @Test
    void shouldRejectDecimalPlacesOutsideZeroToEightRange() {
        assertThrows(IllegalArgumentException.class, () -> new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", -1));
        assertThrows(IllegalArgumentException.class, () -> new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 9));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFields() {
        assertThrows(NullPointerException.class, () -> new EconomyCurrency(null, "Gems", "G", 0));
        assertThrows(NullPointerException.class, () -> new EconomyCurrency(CurrencyId.of("gems"), null, "G", 0));
        assertThrows(NullPointerException.class, () -> new EconomyCurrency(CurrencyId.of("gems"), "Gems", null, 0));
    }

    @Test
    void shouldImplementValueEquality() {
        assertEquals(
                new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 0),
                new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 0));
        assertNotEquals(
                new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 0),
                new EconomyCurrency(CurrencyId.of("gems"), "Gems", "G", 1));
    }
}

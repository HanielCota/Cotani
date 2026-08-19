package com.cotani.economy.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CurrencyDefinitionTest {
    private static final EconomyCurrency CURRENCY = new EconomyCurrency(CurrencyId.of("coins"), "Coins", "$", 2);

    @Test
    void shouldAcceptValidDefinition() {
        var definition = new CurrencyDefinition(
                CURRENCY, BigDecimal.ZERO, new BigDecimal("1000.00"), new BigDecimal("100.00"), BigDecimal.ONE, true);

        assertEquals(CURRENCY, definition.currency());
        assertEquals(0, definition.startingBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, definition.maximumBalance().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, definition.maximumOperationAmount().compareTo(new BigDecimal("100.00")));
        assertEquals(0, definition.minimumPayAmount().compareTo(BigDecimal.ONE));
        assertTrue(definition.enabled());
    }

    @Test
    void shouldRejectNegativeStartingBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY,
                        BigDecimal.valueOf(-1),
                        new BigDecimal("1000"),
                        new BigDecimal("100"),
                        BigDecimal.ONE,
                        true));
    }

    @Test
    void shouldRejectNonPositiveMaximumBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY, BigDecimal.ZERO, BigDecimal.valueOf(-1), BigDecimal.ONE, BigDecimal.ONE, true));
    }

    @Test
    void shouldRejectNonPositiveOrOverMaximumOperationAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("101"), BigDecimal.ONE, true));
    }

    @Test
    void shouldRejectNegativeMinimumPayAmountOrAboveMaximumOperationAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY,
                        BigDecimal.ZERO,
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        BigDecimal.valueOf(-1),
                        true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY,
                        BigDecimal.ZERO,
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        new BigDecimal("11"),
                        true));
    }

    @Test
    void shouldRejectStartingBalanceAboveMaximumBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrencyDefinition(
                        CURRENCY,
                        new BigDecimal("101"),
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        BigDecimal.ONE,
                        true));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullFields() {
        assertThrows(
                NullPointerException.class,
                () -> new CurrencyDefinition(
                        null, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ONE, true));
        assertThrows(
                NullPointerException.class,
                () -> new CurrencyDefinition(
                        CURRENCY, null, new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ONE, true));
        assertThrows(
                NullPointerException.class,
                () -> new CurrencyDefinition(CURRENCY, BigDecimal.ZERO, null, BigDecimal.ONE, BigDecimal.ONE, true));
        assertThrows(
                NullPointerException.class,
                () -> new CurrencyDefinition(
                        CURRENCY, BigDecimal.ZERO, new BigDecimal("100"), null, BigDecimal.ONE, true));
        assertThrows(
                NullPointerException.class,
                () -> new CurrencyDefinition(
                        CURRENCY, BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ONE, null, true));
    }

    @Test
    void shouldImplementValueEquality() {
        var first = new CurrencyDefinition(
                CURRENCY, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ONE, true);
        var second = new CurrencyDefinition(
                CURRENCY, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ONE, true);
        var disabled = new CurrencyDefinition(
                CURRENCY, BigDecimal.ZERO, new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ONE, false);

        assertEquals(first, second);
        assertNotEquals(first, disabled);
    }
}

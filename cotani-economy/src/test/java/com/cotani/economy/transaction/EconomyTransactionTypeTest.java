package com.cotani.economy.transaction;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EconomyTransactionTypeTest {

    @Test
    void shouldContainAllSupportedTransactionTypes() {
        assertEquals(
                Set.of("DEPOSIT", "WITHDRAW", "TRANSFER", "SET"),
                Set.of(
                        EconomyTransactionType.DEPOSIT.name(),
                        EconomyTransactionType.WITHDRAW.name(),
                        EconomyTransactionType.TRANSFER.name(),
                        EconomyTransactionType.SET.name()));
    }

    @Test
    void shouldExposeExactlyFourValues() {
        assertEquals(4, EconomyTransactionType.values().length);
    }

    @Test
    void shouldRoundTripByName() {
        for (var type : EconomyTransactionType.values()) {
            assertSame(Enum.valueOf(EconomyTransactionType.class, type.name()), type);
        }
    }
}

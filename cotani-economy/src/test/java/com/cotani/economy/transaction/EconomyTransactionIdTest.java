package com.cotani.economy.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyTransactionIdTest {

    @Test
    void shouldGenerateUniqueRandomTransactionIds() {
        var ids = new HashSet<EconomyTransactionId>();

        for (int i = 0; i < 100; i++) {
            ids.add(EconomyTransactionId.random());
        }

        assertEquals(100, ids.size());
    }

    @Test
    void shouldPreserveUuidValue() {
        var uuid = UUID.randomUUID();

        var transactionId = new EconomyTransactionId(uuid);

        assertEquals(uuid, transactionId.value());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullUuid() {
        assertThrows(NullPointerException.class, () -> new EconomyTransactionId(null));
    }

    @Test
    void shouldImplementValueEqualityAndHashCode() {
        var uuid = UUID.randomUUID();

        assertEquals(new EconomyTransactionId(uuid), new EconomyTransactionId(uuid));
        assertEquals(new EconomyTransactionId(uuid).hashCode(), new EconomyTransactionId(uuid).hashCode());
        assertNotEquals(EconomyTransactionId.random(), EconomyTransactionId.random());
    }
}

package com.cotani.economy.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyOperationIdTest {

    @Test
    void shouldGenerateUniqueRandomOperationIds() {
        var ids = new HashSet<EconomyOperationId>();

        for (int i = 0; i < 100; i++) {
            ids.add(EconomyOperationId.random());
        }

        assertEquals(100, ids.size());
    }

    @Test
    void shouldCreateOperationIdFromUuidAndPreserveTheValue() {
        var uuid = UUID.randomUUID();

        var operationId = EconomyOperationId.of(uuid);

        assertEquals(uuid, operationId.value());
    }

    @Test
    void shouldRoundTripThroughValue() {
        var operationId = EconomyOperationId.random();

        assertEquals(operationId, EconomyOperationId.of(operationId.value()));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullUuid() {
        assertThrows(NullPointerException.class, () -> EconomyOperationId.of(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullOnDirectConstruction() {
        assertThrows(NullPointerException.class, () -> new EconomyOperationId(null));
    }

    @Test
    void shouldImplementValueEqualityAndHashCode() {
        var uuid = UUID.randomUUID();
        var first = EconomyOperationId.of(uuid);
        var second = EconomyOperationId.of(uuid);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, EconomyOperationId.random());
    }

    @Test
    void shouldBeUsableAsMapKey() {
        var operationId = EconomyOperationId.random();

        var stored = new HashSet<EconomyOperationId>();
        stored.add(operationId);
        stored.add(EconomyOperationId.of(operationId.value()));

        assertEquals(1, stored.size());
        assertTrue(stored.contains(operationId));
        assertNotNull(operationId.toString());
    }
}

package com.cotani.audit.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDetailsCodecTest {
    @Test
    void roundTripsUnicodeSeparatorsAndEmptyValues() {
        var details = Map.of("message", "ação|com.pontos", "empty", "");

        assertEquals(details, AuditDetailsCodec.decode(AuditDetailsCodec.encode(details)));
    }

    @Test
    void rejectsDuplicateEncodedKeys() {
        var encoded = AuditDetailsCodec.encode(Map.of("key", "value"))
                + "|"
                + AuditDetailsCodec.encode(Map.of("key", "other"));

        assertThrows(IllegalArgumentException.class, () -> AuditDetailsCodec.decode(encoded));
    }
}

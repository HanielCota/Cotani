package com.cotani.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class BinaryInventorySerializerValidationTest {

    @Test
    void shouldRejectInvalidSnapshotHeader() {
        var invalidPayload =
                ByteBuffer.allocate(Integer.BYTES * 2).putInt(0).putInt(1).array();

        assertThrows(
                IllegalArgumentException.class,
                () -> CotaniInventories.binarySerializer().deserialize(invalidPayload));
    }
}

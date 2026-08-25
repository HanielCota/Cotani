package com.cotani.inventory.api;

import java.util.Base64;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Serializer interface responsible for encoding and decoding {@link InventorySnapshot}
 * into loss-less binary format or Base64 representation.
 */
@NullMarked
public interface InventorySerializer {

    /**
     * Serializes a snapshot into a loss-less binary byte array.
     *
     * @param snapshot the inventory snapshot
     * @return binary byte array
     */
    byte[] serialize(InventorySnapshot snapshot);

    /**
     * Deserializes a binary byte array back into an {@link InventorySnapshot}.
     *
     * @param data binary byte array
     * @return deserialized inventory snapshot
     */
    InventorySnapshot deserialize(byte[] data);

    /**
     * Serializes a snapshot into a Base64-encoded string.
     *
     * @param snapshot the inventory snapshot
     * @return base64 string
     */
    default String toBase64(InventorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return Base64.getEncoder().encodeToString(serialize(snapshot));
    }

    /**
     * Deserializes a Base64-encoded string back into an {@link InventorySnapshot}.
     *
     * @param base64 base64 string
     * @return deserialized inventory snapshot
     */
    default InventorySnapshot fromBase64(String base64) {
        Objects.requireNonNull(base64, "base64");
        return deserialize(Base64.getDecoder().decode(base64));
    }
}

package com.cotani.npc.api;

import java.util.Objects;

/**
 * Immutable skin representation for a virtual NPC consisting of base64 texture value and signature.
 *
 * @param value base64 encoded texture data
 * @param signature base64 encoded cryptographic signature from Mojang (or empty if unauthenticated)
 */
public record NpcSkin(String value, String signature) {

    public static final NpcSkin EMPTY = new NpcSkin("", "");

    public NpcSkin {
        Objects.requireNonNull(value, "Parameter 'value' must not be null");
        Objects.requireNonNull(signature, "Parameter 'signature' must not be null");
    }

    /**
     * Creates an NpcSkin with texture value and signature.
     *
     * @param value base64 texture value
     * @param signature base64 signature
     * @return NpcSkin instance
     */
    public static NpcSkin of(String value, String signature) {
        return new NpcSkin(value, signature);
    }

    /**
     * Creates an unsigned NpcSkin with texture value only.
     *
     * @param value base64 texture value
     * @return NpcSkin instance
     */
    public static NpcSkin of(String value) {
        return new NpcSkin(value, "");
    }

    /**
     * Whether this skin has valid texture data.
     *
     * @return true if non-empty
     */
    public boolean isPresent() {
        return !value.isEmpty();
    }
}

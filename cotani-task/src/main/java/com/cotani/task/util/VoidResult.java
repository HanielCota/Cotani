package com.cotani.task.util;

/**
 * Provides a single, safe way to produce the only legal {@link Void} value.
 *
 * <p>Java's {@code Void} type has no instances, so {@code null} is the only value that can be
 * returned when a lambda or generic method requires {@code Void}. NullAway understands this helper
 * as the single allowed source of {@code Void} nulls, keeping call sites clean.
 */
public final class VoidResult {

    private VoidResult() {}

    @SuppressWarnings("NullAway")
    public static Void nullValue() {
        return null;
    }
}

package com.cotani.locale.api;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/** Stable, case-insensitive key for a localized message. */
@NullMarked
public record MessageKey(String value) {
    private static final int MAX_LENGTH = 128;

    public MessageKey {
        Objects.requireNonNull(value, "value");
        var normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Message key must contain 1 to " + MAX_LENGTH + " characters");
        }
        if (!normalized.matches("[a-z0-9][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Message key contains unsupported characters: " + value);
        }
        value = normalized;
    }

    public static MessageKey of(String value) {
        return new MessageKey(value);
    }
}

package com.cotani.location.api;

import java.util.Locale;
import java.util.Objects;

/** Canonical, case-insensitive name used by a home or warp. */
public record LocationName(String value) {
    private static final int MAX_LENGTH = 32;

    public LocationName {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAX_LENGTH || !value.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Location name must contain 1-32 lowercase letters, digits, '_' or '-': " + value);
        }
    }

    public static LocationName of(String value) {
        return new LocationName(value);
    }
}

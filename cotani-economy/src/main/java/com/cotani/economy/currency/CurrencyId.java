package com.cotani.economy.currency;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CurrencyId(String value) {

    private static final Pattern ALLOWED_VALUE = Pattern.compile("^[a-z0-9_-]{2,32}$");

    public CurrencyId {
        Objects.requireNonNull(value, "value");

        value = value.trim().toLowerCase(Locale.ROOT);

        if (!ALLOWED_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Currency id must match " + ALLOWED_VALUE.pattern() + ".");
        }
    }

    public static CurrencyId of(String value) {
        return new CurrencyId(value);
    }
}

package com.cotani.audit.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Normalized action identifier stored in an audit entry. */
public record AuditAction(String value) {
    private static final Pattern VALID_VALUE = Pattern.compile("[a-z0-9._:-]+");

    public AuditAction {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || !VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid audit action: " + value);
        }
    }

    public static AuditAction of(String value) {
        return new AuditAction(value);
    }
}

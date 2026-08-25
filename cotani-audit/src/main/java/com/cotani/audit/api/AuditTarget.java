package com.cotani.audit.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Identifies the resource affected by an audit entry. */
public record AuditTarget(String type, String id) {
    private static final Pattern VALID_TYPE = Pattern.compile("[a-z0-9._:-]+");

    public AuditTarget {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        type = type.trim().toLowerCase(Locale.ROOT);
        id = id.trim();
        if (type.isEmpty() || !VALID_TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("Invalid audit target type: " + type);
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Audit target id must not be blank");
        }
    }

    public static AuditTarget of(String type, String id) {
        return new AuditTarget(type, id);
    }

    public static AuditTarget resource(String type, String id) {
        return of(type, id);
    }
}

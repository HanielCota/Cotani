package com.cotani.audit.api;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Identifies the principal that caused an audit entry. */
public record AuditActor(String type, String id) {
    private static final Pattern VALID_TYPE = Pattern.compile("[a-z0-9._:-]+");

    public AuditActor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        type = type.trim().toLowerCase(Locale.ROOT);
        id = id.trim();
        if (type.isEmpty() || !VALID_TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("Invalid audit actor type: " + type);
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Audit actor id must not be blank");
        }
    }

    public static AuditActor of(String type, String id) {
        return new AuditActor(type, id);
    }

    public static AuditActor player(UUID playerId) {
        return new AuditActor(
                "player", Objects.requireNonNull(playerId, "playerId").toString());
    }

    public static AuditActor system() {
        return new AuditActor("system", "system");
    }
}

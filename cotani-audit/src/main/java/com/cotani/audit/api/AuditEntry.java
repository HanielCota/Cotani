package com.cotani.audit.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, append-only record of a meaningful domain action.
 *
 * <p>Details are bounded metadata and are not encrypted or redacted by Cotani; callers must not
 * place passwords, tokens, or other secrets in them.
 */
public record AuditEntry(
        UUID id,
        Instant occurredAt,
        AuditActor actor,
        AuditAction action,
        AuditTarget target,
        AuditSeverity severity,
        Map<String, String> details) {
    public static final int MAX_DETAIL_ENTRIES = 64;
    public static final int MAX_DETAIL_KEY_LENGTH = 64;
    public static final int MAX_DETAIL_VALUE_LENGTH = 2_048;

    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(details, "details");

        var normalizedDetails = new LinkedHashMap<String, String>();
        if (details.size() > MAX_DETAIL_ENTRIES) {
            throw new IllegalArgumentException("Audit details must contain at most " + MAX_DETAIL_ENTRIES + " entries");
        }
        details.forEach((key, value) -> {
            Objects.requireNonNull(key, "detail key");
            Objects.requireNonNull(value, "detail value");
            var normalizedKey = key.trim();
            if (normalizedKey.isEmpty()) {
                throw new IllegalArgumentException("Audit detail key must not be blank");
            }
            if (normalizedKey.length() > MAX_DETAIL_KEY_LENGTH) {
                throw new IllegalArgumentException("Audit detail key is too long");
            }
            if (value.length() > MAX_DETAIL_VALUE_LENGTH) {
                throw new IllegalArgumentException("Audit detail value is too long");
            }
            if (normalizedDetails.containsKey(normalizedKey)) {
                throw new IllegalArgumentException("Duplicate audit detail key: " + normalizedKey);
            }
            normalizedDetails.put(normalizedKey, value);
        });
        details = Map.copyOf(normalizedDetails);
    }

    public static AuditEntry now(
            AuditActor actor,
            AuditAction action,
            AuditTarget target,
            AuditSeverity severity,
            Map<String, String> details) {
        return new AuditEntry(UUID.randomUUID(), Instant.now(), actor, action, target, severity, details);
    }
}

package com.cotani.punishment.api;

import com.cotani.audit.api.AuditActor;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable idempotent request for creating one punishment. */
public record PunishmentRequest(
        PunishmentId id,
        UUID targetId,
        AuditActor actor,
        PunishmentType type,
        String reason,
        Instant createdAt,
        Optional<Instant> expiresAt) {
    public PunishmentRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Punishment reason must not be blank");
        }
        if (reason.length() > Punishment.MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Punishment reason is too long");
        }
        expiresAt.ifPresent(expires -> {
            if (!expires.isAfter(createdAt)) {
                throw new IllegalArgumentException("Punishment expiration must be after creation");
            }
        });
    }

    public static PunishmentRequest now(
            UUID targetId, AuditActor actor, PunishmentType type, String reason, Optional<Duration> duration) {
        Objects.requireNonNull(duration, "duration");
        return at(Instant.now(), targetId, actor, type, reason, duration);
    }

    /** Creates a request at an explicit instant, which is useful for replay and deterministic tests. */
    public static PunishmentRequest at(
            Instant createdAt,
            UUID targetId,
            AuditActor actor,
            PunishmentType type,
            String reason,
            Optional<Duration> duration) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(duration, "duration");
        var expiresAt = duration.map(value -> {
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("Punishment duration must be positive");
            }
            return createdAt.plus(value);
        });
        return new PunishmentRequest(PunishmentId.random(), targetId, actor, type, reason, createdAt, expiresAt);
    }

    public Punishment toPunishment() {
        return Punishment.create(id, targetId, actor, type, reason, createdAt, expiresAt);
    }
}

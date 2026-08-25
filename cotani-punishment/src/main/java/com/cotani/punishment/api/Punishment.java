package com.cotani.punishment.api;

import com.cotani.audit.api.AuditActor;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable record of a punishment and its optional revocation. */
public record Punishment(
        PunishmentId id,
        UUID targetId,
        AuditActor actor,
        PunishmentType type,
        String reason,
        Instant createdAt,
        Optional<Instant> expiresAt,
        Optional<Revocation> revocation) {
    public static final int MAX_REASON_LENGTH = 512;

    public Punishment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(revocation, "revocation");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Punishment reason must not be blank");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Punishment reason is too long");
        }
        expiresAt.ifPresent(expires -> {
            if (!expires.isAfter(createdAt)) {
                throw new IllegalArgumentException("Punishment expiration must be after creation");
            }
        });
        revocation.ifPresent(value -> {
            if (value.revokedAt().isBefore(createdAt)) {
                throw new IllegalArgumentException("Punishment revocation must not precede creation");
            }
        });
        revocation = revocation.map(Punishment::copy);
    }

    public static Punishment create(
            PunishmentId id,
            UUID targetId,
            AuditActor actor,
            PunishmentType type,
            String reason,
            Instant createdAt,
            Optional<Instant> expiresAt) {
        return new Punishment(id, targetId, actor, type, reason, createdAt, expiresAt, Optional.empty());
    }

    public PunishmentStatus statusAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (instant.isBefore(createdAt)) {
            return PunishmentStatus.NOT_STARTED;
        }
        if (revocation.filter(value -> !instant.isBefore(value.revokedAt())).isPresent()) {
            return PunishmentStatus.REVOKED;
        }
        return expiresAt.filter(expiration -> !instant.isBefore(expiration)).isPresent()
                ? PunishmentStatus.EXPIRED
                : PunishmentStatus.ACTIVE;
    }

    public boolean isActiveAt(Instant instant) {
        return statusAt(instant) == PunishmentStatus.ACTIVE;
    }

    public Punishment revoke(Revocation value) {
        Objects.requireNonNull(value, "value");
        if (revocation.isPresent()) {
            return this;
        }
        return new Punishment(id, targetId, actor, type, reason, createdAt, expiresAt, Optional.of(value));
    }

    private static Revocation copy(Revocation value) {
        return new Revocation(value.actor(), value.reason(), value.revokedAt());
    }

    /** Immutable explanation of a revocation. */
    public record Revocation(AuditActor actor, String reason, Instant revokedAt) {
        public Revocation {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(revokedAt, "revokedAt");
            reason = reason.trim();
            if (reason.isEmpty()) {
                throw new IllegalArgumentException("Revocation reason must not be blank");
            }
            if (reason.length() > MAX_REASON_LENGTH) {
                throw new IllegalArgumentException("Revocation reason is too long");
            }
        }
    }
}

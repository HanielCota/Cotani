package com.cotani.punishment.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable bounded query for punishment history or effective state with cursor pagination. */
public record PunishmentQuery(
        Optional<UUID> targetId,
        Optional<PunishmentType> type,
        Optional<Instant> activeAt,
        Optional<PunishmentCursor> before,
        int limit) {
    public PunishmentQuery {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(activeAt, "activeAt");
        Objects.requireNonNull(before, "before");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("Punishment query limit must be between 1 and 1000");
        }
    }

    public static PunishmentQuery all() {
        return new PunishmentQuery(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 100);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Optional<UUID> targetId = Optional.empty();
        private Optional<PunishmentType> type = Optional.empty();
        private Optional<Instant> activeAt = Optional.empty();
        private Optional<PunishmentCursor> before = Optional.empty();
        private int limit = 100;

        public Builder targetId(UUID value) {
            targetId = Optional.of(Objects.requireNonNull(value, "targetId"));
            return this;
        }

        public Builder type(PunishmentType value) {
            type = Optional.of(Objects.requireNonNull(value, "type"));
            return this;
        }

        public Builder activeAt(Instant value) {
            activeAt = Optional.of(Objects.requireNonNull(value, "activeAt"));
            return this;
        }

        public Builder before(PunishmentCursor value) {
            before = Optional.of(Objects.requireNonNull(value, "before"));
            return this;
        }

        public Builder limit(int value) {
            limit = value;
            return this;
        }

        public PunishmentQuery build() {
            return new PunishmentQuery(targetId, type, activeAt, before, limit);
        }
    }
}

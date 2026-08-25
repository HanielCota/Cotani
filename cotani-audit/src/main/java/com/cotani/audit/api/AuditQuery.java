package com.cotani.audit.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable filter for bounded audit queries, optionally continuing from an older cursor. */
public record AuditQuery(
        Optional<AuditAction> action,
        Optional<AuditActor> actor,
        Optional<AuditTarget> target,
        Optional<Instant> from,
        Optional<Instant> until,
        Optional<AuditCursor> before,
        int limit) {
    public AuditQuery {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(before, "before");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("Audit query limit must be between 1 and 1000");
        }
        if (from.isPresent() && until.isPresent() && from.get().isAfter(until.get())) {
            throw new IllegalArgumentException("Audit query from must not be after until");
        }
    }

    public static AuditQuery all() {
        return new AuditQuery(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                100);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Optional<AuditAction> action = Optional.empty();
        private Optional<AuditActor> actor = Optional.empty();
        private Optional<AuditTarget> target = Optional.empty();
        private Optional<Instant> from = Optional.empty();
        private Optional<Instant> until = Optional.empty();
        private Optional<AuditCursor> before = Optional.empty();
        private int limit = 100;

        public Builder action(AuditAction action) {
            this.action = Optional.of(Objects.requireNonNull(action, "action"));
            return this;
        }

        public Builder actor(AuditActor actor) {
            this.actor = Optional.of(Objects.requireNonNull(actor, "actor"));
            return this;
        }

        public Builder target(AuditTarget target) {
            this.target = Optional.of(Objects.requireNonNull(target, "target"));
            return this;
        }

        public Builder from(Instant from) {
            this.from = Optional.of(Objects.requireNonNull(from, "from"));
            return this;
        }

        public Builder until(Instant until) {
            this.until = Optional.of(Objects.requireNonNull(until, "until"));
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder before(AuditCursor before) {
            this.before = Optional.of(Objects.requireNonNull(before, "before"));
            return this;
        }

        public AuditQuery build() {
            return new AuditQuery(action, actor, target, from, until, before, limit);
        }
    }
}

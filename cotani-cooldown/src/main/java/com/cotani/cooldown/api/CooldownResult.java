package com.cotani.cooldown.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record CooldownResult(
        CooldownState state,
        CooldownKey key,
        Duration remaining,
        @Nullable Instant expiresAt) {

    public CooldownResult {
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(remaining, "remaining cannot be null");
    }

    public boolean allowed() {
        return state == CooldownState.ALLOWED;
    }

    public boolean denied() {
        return state == CooldownState.DENIED;
    }

    public Optional<Instant> expiresAtOptional() {
        return Optional.ofNullable(expiresAt);
    }

    public static CooldownResult allowed(CooldownKey key) {
        return new CooldownResult(CooldownState.ALLOWED, key, Duration.ZERO, null);
    }

    public static CooldownResult denied(CooldownKey key, Duration remaining, Instant expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");

        return new CooldownResult(CooldownState.DENIED, key, remaining, expiresAt);
    }
}

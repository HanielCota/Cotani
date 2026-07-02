package com.cotani.economy.transaction;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public record EconomyReason(
        String key, String source, @Nullable UUID actorUserId) {

    private static final Pattern ALLOWED_KEY = Pattern.compile("^[a-z0-9_.:-]{2,96}$");
    private static final Pattern ALLOWED_SOURCE = Pattern.compile("^[a-z0-9_.:-]{2,64}$");

    public EconomyReason {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");

        key = key.trim().toLowerCase(Locale.ROOT);
        source = source.trim().toLowerCase(Locale.ROOT);

        if (!ALLOWED_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Economy reason key must match " + ALLOWED_KEY.pattern() + ".");
        }

        if (!ALLOWED_SOURCE.matcher(source).matches()) {
            throw new IllegalArgumentException("Economy reason source must match " + ALLOWED_SOURCE.pattern() + ".");
        }
    }

    public static EconomyReason system(String key) {
        return new EconomyReason(key, "cotani", null);
    }

    public static EconomyReason plugin(String key, String source) {
        return new EconomyReason(key, source, null);
    }

    public static EconomyReason player(String key, UUID actorUserId) {
        return new EconomyReason(key, "player", Objects.requireNonNull(actorUserId, "actorUserId"));
    }

    public Optional<UUID> actor() {
        return Optional.ofNullable(actorUserId);
    }
}

package com.cotani.teleport.api;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

public record TeleportRequest(
        UUID playerId, Location target, TeleportCause cause, TeleportOptions options, String source) {

    private static final String PLAYER_ID_PARAM = "playerId";
    private static final String TARGET_PARAM = "target";

    public TeleportRequest {
        Objects.requireNonNull(playerId, PLAYER_ID_PARAM);
        Objects.requireNonNull(target, TARGET_PARAM);
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(options, "options");
        target = target.clone();
        if (source == null || source.isBlank()) {
            source = "unknown";
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable UUID playerId;
        private @Nullable Location target;
        private TeleportCause cause = TeleportCause.UNKNOWN;
        private TeleportOptions options = TeleportOptions.defaults();
        private String source = "unknown";

        public Builder playerId(UUID playerId) {
            this.playerId = Objects.requireNonNull(playerId, PLAYER_ID_PARAM);
            return this;
        }

        public Builder target(Location target) {
            this.target = Objects.requireNonNull(target, TARGET_PARAM);
            return this;
        }

        public Builder cause(TeleportCause cause) {
            this.cause = Objects.requireNonNull(cause, "cause");
            return this;
        }

        public Builder options(TeleportOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public TeleportRequest build() {
            return new TeleportRequest(
                    Objects.requireNonNull(playerId, PLAYER_ID_PARAM),
                    Objects.requireNonNull(target, TARGET_PARAM),
                    cause,
                    options,
                    source);
        }
    }
}

package com.cotani.teleport.api;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public record TeleportRequest(
        Player player, Location target, TeleportCause cause, TeleportOptions options, String source) {
    public TeleportRequest {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
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
        private @Nullable Player player;
        private @Nullable Location target;
        private TeleportCause cause = TeleportCause.UNKNOWN;
        private TeleportOptions options = TeleportOptions.defaults();
        private String source = "unknown";

        public Builder player(Player player) {
            this.player = Objects.requireNonNull(player, "player");
            return this;
        }

        public Builder target(Location target) {
            this.target = Objects.requireNonNull(target, "target");
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
                    Objects.requireNonNull(player, "player"),
                    Objects.requireNonNull(target, "target"),
                    cause,
                    options,
                    source);
        }
    }
}

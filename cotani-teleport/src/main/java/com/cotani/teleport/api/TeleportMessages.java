package com.cotani.teleport.api;

import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class TeleportMessages {
    private final Component blockedByCombat;
    private final Component blockedByCooldown;
    private final Component blockedByPermission;
    private final Component blockedByRegion;

    private TeleportMessages(Builder builder) {
        this.blockedByCombat = Objects.requireNonNull(builder.blockedByCombat, "blockedByCombat");
        this.blockedByCooldown = Objects.requireNonNull(builder.blockedByCooldown, "blockedByCooldown");
        this.blockedByPermission = Objects.requireNonNull(builder.blockedByPermission, "blockedByPermission");
        this.blockedByRegion = Objects.requireNonNull(builder.blockedByRegion, "blockedByRegion");
    }

    public static TeleportMessages defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Component blockedByCombat() {
        return blockedByCombat;
    }

    public Component blockedByCooldown() {
        return blockedByCooldown;
    }

    public Component blockedByPermission() {
        return blockedByPermission;
    }

    public Component blockedByRegion() {
        return blockedByRegion;
    }

    public static final class Builder {
        private Component blockedByCombat = Component.text("Você não pode teleportar em combate.");
        private Component blockedByCooldown = Component.text("Aguarde antes de teleportar novamente.");
        private Component blockedByPermission = Component.text("Você não tem permissão para teleportar.");
        private Component blockedByRegion = Component.text("Você não pode teleportar para esta região.");

        public Builder blockedByCombat(Component message) {
            this.blockedByCombat = Objects.requireNonNull(message, "message");
            return this;
        }

        public Builder blockedByCooldown(Component message) {
            this.blockedByCooldown = Objects.requireNonNull(message, "message");
            return this;
        }

        public Builder blockedByPermission(Component message) {
            this.blockedByPermission = Objects.requireNonNull(message, "message");
            return this;
        }

        public Builder blockedByRegion(Component message) {
            this.blockedByRegion = Objects.requireNonNull(message, "message");
            return this;
        }

        public TeleportMessages build() {
            return new TeleportMessages(this);
        }
    }
}

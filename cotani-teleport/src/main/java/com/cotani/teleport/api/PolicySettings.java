package com.cotani.teleport.api;

import java.time.Duration;
import java.util.Objects;

public record PolicySettings(
        boolean checkCombat,
        boolean checkCooldown,
        Duration cooldownDuration,
        boolean checkPermission,
        boolean checkRegion) {
    public static PolicySettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(PolicySettings base) {
        return new Builder(base);
    }

    public static final class Builder {
        private boolean checkCombat = true;
        private boolean checkCooldown = true;
        private Duration cooldownDuration = Duration.ofSeconds(30);
        private boolean checkPermission = false;
        private boolean checkRegion = true;

        public Builder() {}

        public Builder(PolicySettings base) {
            this.checkCombat = base.checkCombat();
            this.checkCooldown = base.checkCooldown();
            this.cooldownDuration = base.cooldownDuration();
            this.checkPermission = base.checkPermission();
            this.checkRegion = base.checkRegion();
        }

        public Builder checkCombat(boolean checkCombat) {
            this.checkCombat = checkCombat;
            return this;
        }

        public Builder checkCooldown(boolean checkCooldown) {
            this.checkCooldown = checkCooldown;
            return this;
        }

        public Builder cooldownDuration(Duration cooldownDuration) {
            this.cooldownDuration = Objects.requireNonNull(cooldownDuration, "cooldownDuration");
            return this;
        }

        public Builder checkPermission(boolean checkPermission) {
            this.checkPermission = checkPermission;
            return this;
        }

        public Builder checkRegion(boolean checkRegion) {
            this.checkRegion = checkRegion;
            return this;
        }

        public PolicySettings build() {
            return new PolicySettings(checkCombat, checkCooldown, cooldownDuration, checkPermission, checkRegion);
        }
    }
}

package com.cotani.teleport.api;

public record PlayerSettings(boolean preserveVelocity, boolean dismount, boolean closeInventory) {
    public static PlayerSettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean preserveVelocity = false;
        private boolean dismount = true;
        private boolean closeInventory = true;

        public Builder preserveVelocity(boolean preserveVelocity) {
            this.preserveVelocity = preserveVelocity;
            return this;
        }

        public Builder dismount(boolean dismount) {
            this.dismount = dismount;
            return this;
        }

        public Builder closeInventory(boolean closeInventory) {
            this.closeInventory = closeInventory;
            return this;
        }

        public PlayerSettings build() {
            return new PlayerSettings(preserveVelocity, dismount, closeInventory);
        }
    }
}

package com.cotani.teleport.api;

import java.util.Objects;

public record SafetySettings(boolean safeLocation, SafeLocationOptions safeLocationOptions) {

    public SafetySettings {
        Objects.requireNonNull(safeLocationOptions, "safeLocationOptions");
    }

    public static SafetySettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(SafetySettings base) {
        return new Builder(base);
    }

    public static final class Builder {
        private boolean safeLocation = true;
        private SafeLocationOptions safeLocationOptions = SafeLocationOptions.defaults();

        public Builder() {}

        public Builder(SafetySettings base) {
            this.safeLocation = base.safeLocation();
            this.safeLocationOptions = base.safeLocationOptions();
        }

        public Builder safeLocation(boolean safeLocation) {
            this.safeLocation = safeLocation;
            return this;
        }

        public Builder safeLocationOptions(SafeLocationOptions safeLocationOptions) {
            this.safeLocationOptions = safeLocationOptions;
            return this;
        }

        public SafetySettings build() {
            return new SafetySettings(safeLocation, safeLocationOptions);
        }
    }
}

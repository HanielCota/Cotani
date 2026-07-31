package com.cotani.teleport.api;

import java.time.Duration;
import java.util.Objects;

public record ExecutionSettings(boolean async, Duration reconciliationTimeout) {
    public ExecutionSettings(boolean async) {
        this(async, Duration.ofSeconds(30));
    }

    public ExecutionSettings {
        Objects.requireNonNull(reconciliationTimeout, "reconciliationTimeout");

        if (!reconciliationTimeout.isPositive()) {
            throw new IllegalArgumentException("reconciliationTimeout must be positive");
        }
    }

    public static ExecutionSettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ExecutionSettings base) {
        return new Builder(base);
    }

    public static final class Builder {
        private boolean async = true;
        private Duration reconciliationTimeout = Duration.ofSeconds(30);

        public Builder() {}

        public Builder(ExecutionSettings base) {
            this.async = base.async();
            this.reconciliationTimeout = base.reconciliationTimeout();
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder reconciliationTimeout(Duration reconciliationTimeout) {
            this.reconciliationTimeout = Objects.requireNonNull(reconciliationTimeout, "reconciliationTimeout");
            return this;
        }

        public ExecutionSettings build() {
            return new ExecutionSettings(async, reconciliationTimeout);
        }
    }
}

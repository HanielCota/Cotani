package com.cotani.teleport.api;

public record ExecutionSettings(boolean async) {
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

        public Builder() {}

        public Builder(ExecutionSettings base) {
            this.async = base.async();
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public ExecutionSettings build() {
            return new ExecutionSettings(async);
        }
    }
}

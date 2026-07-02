package com.cotani.teleport.api;

public record FeedbackSettings(boolean playEffects, boolean sendMessages) {

    public static FeedbackSettings defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(FeedbackSettings base) {
        return new Builder(base);
    }

    public static final class Builder {
        private boolean playEffects = true;
        private boolean sendMessages = true;

        public Builder() {}

        public Builder(FeedbackSettings base) {
            this.playEffects = base.playEffects();
            this.sendMessages = base.sendMessages();
        }

        public Builder playEffects(boolean playEffects) {
            this.playEffects = playEffects;
            return this;
        }

        public Builder sendMessages(boolean sendMessages) {
            this.sendMessages = sendMessages;
            return this;
        }

        public FeedbackSettings build() {
            return new FeedbackSettings(playEffects, sendMessages);
        }
    }
}

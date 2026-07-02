package com.cotani.teleport.api;

import java.time.Duration;
import java.util.Objects;

public record TeleportOptions(
        ExecutionSettings execution,
        SafetySettings safety,
        PolicySettings policies,
        PlayerSettings player,
        FeedbackSettings feedback,
        Duration timeout) {
    public TeleportOptions {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(safety, "safety");
        Objects.requireNonNull(policies, "policies");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(10);
        }
    }

    public static TeleportOptions defaults() {
        return builder().build();
    }

    public static TeleportOptions spawn() {
        return builder()
                .policies(PolicySettings.builder()
                        .checkCombat(true)
                        .checkCooldown(true)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(true).build())
                .build();
    }

    public static TeleportOptions admin() {
        return builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(false)
                        .checkPermission(false)
                        .checkRegion(false)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .build();
    }

    public static TeleportOptions silent() {
        return builder()
                .feedback(FeedbackSettings.builder()
                        .playEffects(false)
                        .sendMessages(false)
                        .build())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean async() {
        return execution.async();
    }

    public boolean safeLocation() {
        return safety.safeLocation();
    }

    public SafeLocationOptions safeLocationOptions() {
        return safety.safeLocationOptions();
    }

    public boolean checkCombat() {
        return policies.checkCombat();
    }

    public boolean checkCooldown() {
        return policies.checkCooldown();
    }

    public Duration cooldownDuration() {
        return policies.cooldownDuration();
    }

    public boolean checkPermission() {
        return policies.checkPermission();
    }

    public boolean checkRegion() {
        return policies.checkRegion();
    }

    public boolean preserveVelocity() {
        return player.preserveVelocity();
    }

    public boolean dismount() {
        return player.dismount();
    }

    public boolean closeInventory() {
        return player.closeInventory();
    }

    public boolean playEffects() {
        return feedback.playEffects();
    }

    public boolean sendMessages() {
        return feedback.sendMessages();
    }

    public static final class Builder {
        private ExecutionSettings execution = ExecutionSettings.defaults();
        private SafetySettings safety = SafetySettings.defaults();
        private PolicySettings policies = PolicySettings.defaults();
        private PlayerSettings player = PlayerSettings.defaults();
        private FeedbackSettings feedback = FeedbackSettings.defaults();
        private Duration timeout = Duration.ofSeconds(10);

        public Builder execution(ExecutionSettings execution) {
            this.execution = execution;
            return this;
        }

        public Builder safety(SafetySettings safety) {
            this.safety = safety;
            return this;
        }

        public Builder policies(PolicySettings policies) {
            this.policies = policies;
            return this;
        }

        public Builder player(PlayerSettings player) {
            this.player = player;
            return this;
        }

        public Builder feedback(FeedbackSettings feedback) {
            this.feedback = feedback;
            return this;
        }

        public Builder async(boolean async) {
            this.execution =
                    ExecutionSettings.builder(this.execution).async(async).build();
            return this;
        }

        public Builder safeLocation(boolean safeLocation) {
            this.safety = SafetySettings.builder(this.safety)
                    .safeLocation(safeLocation)
                    .build();
            return this;
        }

        public Builder safeLocationOptions(SafeLocationOptions safeLocationOptions) {
            this.safety = SafetySettings.builder(this.safety)
                    .safeLocationOptions(safeLocationOptions)
                    .build();
            return this;
        }

        public Builder checkCombat(boolean checkCombat) {
            this.policies = PolicySettings.builder(this.policies)
                    .checkCombat(checkCombat)
                    .build();
            return this;
        }

        public Builder checkCooldown(boolean checkCooldown) {
            this.policies = PolicySettings.builder(this.policies)
                    .checkCooldown(checkCooldown)
                    .build();
            return this;
        }

        public Builder cooldownDuration(Duration cooldownDuration) {
            this.policies = PolicySettings.builder(this.policies)
                    .cooldownDuration(cooldownDuration)
                    .build();
            return this;
        }

        public Builder checkPermission(boolean checkPermission) {
            this.policies = PolicySettings.builder(this.policies)
                    .checkPermission(checkPermission)
                    .build();
            return this;
        }

        public Builder checkRegion(boolean checkRegion) {
            this.policies = PolicySettings.builder(this.policies)
                    .checkRegion(checkRegion)
                    .build();
            return this;
        }

        public Builder preserveVelocity(boolean preserveVelocity) {
            this.player = PlayerSettings.builder(this.player)
                    .preserveVelocity(preserveVelocity)
                    .build();
            return this;
        }

        public Builder dismount(boolean dismount) {
            this.player = PlayerSettings.builder(this.player).dismount(dismount).build();
            return this;
        }

        public Builder closeInventory(boolean closeInventory) {
            this.player = PlayerSettings.builder(this.player)
                    .closeInventory(closeInventory)
                    .build();
            return this;
        }

        public Builder playEffects(boolean playEffects) {
            this.feedback = FeedbackSettings.builder(this.feedback)
                    .playEffects(playEffects)
                    .build();
            return this;
        }

        public Builder sendMessages(boolean sendMessages) {
            this.feedback = FeedbackSettings.builder(this.feedback)
                    .sendMessages(sendMessages)
                    .build();
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TeleportOptions build() {
            return new TeleportOptions(execution, safety, policies, player, feedback, timeout);
        }
    }
}

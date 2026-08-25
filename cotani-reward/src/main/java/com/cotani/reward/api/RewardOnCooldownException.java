package com.cotani.reward.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a player tries to claim before the reward cooldown ends. */
public final class RewardOnCooldownException extends RewardException {
    private static final long serialVersionUID = 1L;
    private final transient UUID playerId;
    private final transient RewardId rewardId;
    private final transient Instant availableAt;

    public RewardOnCooldownException(UUID playerId, RewardId rewardId, Instant availableAt) {
        super("Reward " + Objects.requireNonNull(rewardId, "rewardId").value() + " is on cooldown until "
                + Objects.requireNonNull(availableAt, "availableAt"));
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.rewardId = rewardId;
        this.availableAt = availableAt;
    }

    public UUID playerId() {
        return playerId;
    }

    public RewardId rewardId() {
        return rewardId;
    }

    public Instant availableAt() {
        return availableAt;
    }
}

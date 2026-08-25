package com.cotani.reward.api;

import java.util.Objects;

/** Raised when a service claim references an unregistered reward. */
public final class RewardNotFoundException extends RewardException {
    private static final long serialVersionUID = 1L;
    private final transient RewardId rewardId;

    public RewardNotFoundException(RewardId rewardId) {
        super("Reward is not registered: "
                + Objects.requireNonNull(rewardId, "rewardId").value());
        this.rewardId = rewardId;
    }

    public RewardId rewardId() {
        return rewardId;
    }
}

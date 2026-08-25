package com.cotani.reward.api;

import java.util.Objects;

/** Raised when an idempotency key is reused for a different player or reward. */
public final class RewardClaimConflictException extends RewardException {
    private static final long serialVersionUID = 1L;
    private final transient RewardClaimId claimId;

    public RewardClaimConflictException(RewardClaimId claimId) {
        super("Reward claim id was already used for another logical claim: "
                + Objects.requireNonNull(claimId, "claimId").value());
        this.claimId = claimId;
    }

    public RewardClaimId claimId() {
        return claimId;
    }
}

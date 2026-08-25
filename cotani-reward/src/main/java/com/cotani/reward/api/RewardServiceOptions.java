package com.cotani.reward.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits for reward repository calls and idempotency retention. */
public record RewardServiceOptions(Duration repositoryTimeout, Duration claimRetention) {
    public RewardServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        Objects.requireNonNull(claimRetention, "claimRetention");
        if (repositoryTimeout.isZero() || repositoryTimeout.isNegative()) {
            throw new IllegalArgumentException("repositoryTimeout must be positive");
        }
        if (claimRetention.isZero() || claimRetention.isNegative()) {
            throw new IllegalArgumentException("claimRetention must be positive");
        }
    }

    public static RewardServiceOptions defaults() {
        return new RewardServiceOptions(Duration.ofSeconds(10), Duration.ofDays(90));
    }

    public RewardServiceOptions withRepositoryTimeout(Duration timeout) {
        return new RewardServiceOptions(timeout, claimRetention);
    }

    public RewardServiceOptions withClaimRetention(Duration retention) {
        return new RewardServiceOptions(repositoryTimeout, retention);
    }

    /** Applies a caller-visible timeout without cancelling the durable repository operation. */
    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, repositoryTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

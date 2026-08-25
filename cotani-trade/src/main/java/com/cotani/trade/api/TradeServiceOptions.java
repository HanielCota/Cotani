package com.cotani.trade.api;

import java.time.Duration;
import java.util.Objects;

/** Operational limits and deadlines for trade orchestration. */
public record TradeServiceOptions(
        int maximumAssetsPerParticipant,
        int maximumEncodedBytesPerParticipant,
        int maximumRetainedTerminalTrades,
        Duration repositoryTimeout,
        Duration settlementTimeout,
        Duration eventTimeout) {
    public TradeServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        Objects.requireNonNull(settlementTimeout, "settlementTimeout");
        Objects.requireNonNull(eventTimeout, "eventTimeout");
        if (maximumAssetsPerParticipant < 1) {
            throw new IllegalArgumentException("maximumAssetsPerParticipant must be positive");
        }
        if (maximumEncodedBytesPerParticipant < 1) {
            throw new IllegalArgumentException("maximumEncodedBytesPerParticipant must be positive");
        }
        if (maximumRetainedTerminalTrades < 1) {
            throw new IllegalArgumentException("maximumRetainedTerminalTrades must be positive");
        }
        validatePositive(repositoryTimeout, "repositoryTimeout");
        validatePositive(settlementTimeout, "settlementTimeout");
        validatePositive(eventTimeout, "eventTimeout");
    }

    /** Compatibility constructor using the default payload and retention limits. */
    public TradeServiceOptions(
            int maximumAssetsPerParticipant,
            Duration repositoryTimeout,
            Duration settlementTimeout,
            Duration eventTimeout) {
        this(maximumAssetsPerParticipant, 4 * 1024 * 1024, 10_000, repositoryTimeout, settlementTimeout, eventTimeout);
    }

    public static TradeServiceOptions defaults() {
        return new TradeServiceOptions(
                64, 4 * 1024 * 1024, 10_000, Duration.ofSeconds(10), Duration.ofSeconds(15), Duration.ofSeconds(5));
    }

    public TradeServiceOptions withMaximumAssetsPerParticipant(int maximumAssets) {
        return new TradeServiceOptions(
                maximumAssets,
                maximumEncodedBytesPerParticipant,
                maximumRetainedTerminalTrades,
                repositoryTimeout,
                settlementTimeout,
                eventTimeout);
    }

    public TradeServiceOptions withMaximumEncodedBytesPerParticipant(int maximumBytes) {
        return new TradeServiceOptions(
                maximumAssetsPerParticipant,
                maximumBytes,
                maximumRetainedTerminalTrades,
                repositoryTimeout,
                settlementTimeout,
                eventTimeout);
    }

    public TradeServiceOptions withMaximumRetainedTerminalTrades(int maximumTrades) {
        return new TradeServiceOptions(
                maximumAssetsPerParticipant,
                maximumEncodedBytesPerParticipant,
                maximumTrades,
                repositoryTimeout,
                settlementTimeout,
                eventTimeout);
    }

    public TradeServiceOptions withRepositoryTimeout(Duration timeout) {
        return new TradeServiceOptions(
                maximumAssetsPerParticipant,
                maximumEncodedBytesPerParticipant,
                maximumRetainedTerminalTrades,
                timeout,
                settlementTimeout,
                eventTimeout);
    }

    public TradeServiceOptions withSettlementTimeout(Duration timeout) {
        return new TradeServiceOptions(
                maximumAssetsPerParticipant,
                maximumEncodedBytesPerParticipant,
                maximumRetainedTerminalTrades,
                repositoryTimeout,
                timeout,
                eventTimeout);
    }

    public TradeServiceOptions withEventTimeout(Duration timeout) {
        return new TradeServiceOptions(
                maximumAssetsPerParticipant,
                maximumEncodedBytesPerParticipant,
                maximumRetainedTerminalTrades,
                repositoryTimeout,
                settlementTimeout,
                timeout);
    }

    private static void validatePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

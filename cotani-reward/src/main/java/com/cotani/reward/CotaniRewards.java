package com.cotani.reward;

import com.cotani.reward.api.RewardGrantHandler;
import com.cotani.reward.api.RewardRepository;
import com.cotani.reward.api.RewardService;
import com.cotani.reward.api.RewardServiceOptions;
import com.cotani.reward.api.RewardSettlementService;
import com.cotani.reward.internal.DefaultRewardService;
import com.cotani.reward.internal.DefaultRewardSettlementService;
import com.cotani.reward.internal.InMemoryRewardRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Factories for the {@code cotani-reward} module. */
public final class CotaniRewards {
    private CotaniRewards() {}

    /** Creates an isolated in-memory reward service, suitable for tests or ephemeral servers. */
    public static RewardService inMemory() {
        return inMemory(RewardServiceOptions.defaults());
    }

    /** Creates an in-memory reward service with explicit operational options. */
    public static RewardService inMemory(RewardServiceOptions options) {
        Objects.requireNonNull(options, "options");
        return DefaultRewardService.create(new InMemoryRewardRepository(), options, Clock.systemUTC());
    }

    /** Creates a service over a caller-owned repository. */
    public static RewardService fromRepository(RewardRepository repository) {
        return fromRepository(repository, RewardServiceOptions.defaults());
    }

    /** Creates a service over a caller-owned repository with explicit options. */
    public static RewardService fromRepository(RewardRepository repository, RewardServiceOptions options) {
        return fromRepository(repository, options, Clock.systemUTC());
    }

    /** Creates a settlement workflow over a reward service and its grant handlers. */
    public static RewardSettlementService settlement(RewardService rewardService, List<RewardGrantHandler> handlers) {
        return new DefaultRewardSettlementService(rewardService, handlers);
    }

    static RewardService fromRepository(RewardRepository repository, RewardServiceOptions options, Clock clock) {
        return DefaultRewardService.create(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }
}

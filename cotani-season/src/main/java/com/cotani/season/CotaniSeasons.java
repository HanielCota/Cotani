package com.cotani.season;

import com.cotani.event.api.EventBus;
import com.cotani.reward.api.RewardService;
import com.cotani.season.api.SeasonRepository;
import com.cotani.season.api.SeasonService;
import com.cotani.season.api.SeasonServiceOptions;
import com.cotani.season.internal.DefaultSeasonService;
import com.cotani.season.internal.InMemorySeasonRepository;
import java.time.Clock;
import java.util.Objects;

/** Factories for the {@code cotani-season} module. */
public final class CotaniSeasons {
    private CotaniSeasons() {}

    /** Creates an isolated in-memory season service for tests or ephemeral servers. */
    public static SeasonService inMemory(RewardService rewardService, EventBus eventBus) {
        return fromRepository(new InMemorySeasonRepository(), rewardService, eventBus, SeasonServiceOptions.defaults());
    }

    /** Creates a service over a caller-owned repository. */
    public static SeasonService fromRepository(
            SeasonRepository repository, RewardService rewardService, EventBus eventBus) {
        return fromRepository(repository, rewardService, eventBus, SeasonServiceOptions.defaults());
    }

    /** Creates a service with explicit operational policies. */
    public static SeasonService fromRepository(
            SeasonRepository repository, RewardService rewardService, EventBus eventBus, SeasonServiceOptions options) {
        return DefaultSeasonService.create(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(rewardService, "rewardService"),
                Objects.requireNonNull(eventBus, "eventBus"),
                Objects.requireNonNull(options, "options"),
                Clock.systemUTC());
    }
}

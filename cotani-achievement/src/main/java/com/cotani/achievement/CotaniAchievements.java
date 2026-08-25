package com.cotani.achievement;

import com.cotani.achievement.api.AchievementRepository;
import com.cotani.achievement.api.AchievementService;
import com.cotani.achievement.api.AchievementServiceOptions;
import com.cotani.achievement.internal.DefaultAchievementService;
import com.cotani.achievement.internal.InMemoryAchievementRepository;
import com.cotani.achievement.storage.StorageAchievementRepository;
import com.cotani.event.api.EventBus;
import com.cotani.reward.api.RewardService;
import com.cotani.statistics.api.StatisticService;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Factories for the {@code cotani-achievement} module. */
public final class CotaniAchievements {
    private CotaniAchievements() {}

    /** Creates an isolated in-memory achievement service. */
    public static AchievementService inMemory(StatisticService statistics, RewardService rewards, EventBus eventBus) {
        return fromRepository(new InMemoryAchievementRepository(), statistics, rewards, eventBus);
    }

    /** Creates a service over a caller-owned repository. */
    public static AchievementService fromRepository(
            AchievementRepository repository, StatisticService statistics, RewardService rewards, EventBus eventBus) {
        return fromRepository(repository, statistics, rewards, eventBus, AchievementServiceOptions.defaults());
    }

    /** Creates a service over a repository with explicit operational options. */
    public static AchievementService fromRepository(
            AchievementRepository repository,
            StatisticService statistics,
            RewardService rewards,
            EventBus eventBus,
            AchievementServiceOptions options) {
        return fromRepository(repository, statistics, rewards, eventBus, options, Clock.systemUTC());
    }

    /** Creates a service with explicit operational options and clock. */
    public static AchievementService fromRepository(
            AchievementRepository repository,
            StatisticService statistics,
            RewardService rewards,
            EventBus eventBus,
            AchievementServiceOptions options,
            Clock clock) {
        return DefaultAchievementService.create(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(statistics, "statistics"),
                Objects.requireNonNull(rewards, "rewards"),
                Objects.requireNonNull(eventBus, "eventBus"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }

    /** Creates a SQL-backed service over caller-owned Cotani storage. */
    public static AchievementService storage(
            CotaniStorage storage, StatisticService statistics, RewardService rewards, EventBus eventBus) {
        return fromRepository(new StorageAchievementRepository(storage), statistics, rewards, eventBus);
    }

    /** Creates a SQL-backed service with explicit operational options. */
    public static AchievementService storage(
            CotaniStorage storage,
            StatisticService statistics,
            RewardService rewards,
            EventBus eventBus,
            AchievementServiceOptions options) {
        return fromRepository(new StorageAchievementRepository(storage), statistics, rewards, eventBus, options);
    }

    /** Returns the migrations required by the SQL adapter. */
    public static List<Migration> migrations() {
        return StorageAchievementRepository.migrations();
    }
}

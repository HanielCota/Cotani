package com.cotani.statistics;

import com.cotani.event.api.EventBus;
import com.cotani.statistics.api.StatisticRepository;
import com.cotani.statistics.api.StatisticService;
import com.cotani.statistics.api.StatisticServiceOptions;
import com.cotani.statistics.internal.DefaultStatisticService;
import com.cotani.statistics.internal.InMemoryStatisticRepository;
import com.cotani.statistics.storage.StorageStatisticRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Factories for the {@code cotani-statistics} module. */
public final class CotaniStatistics {
    private CotaniStatistics() {}

    /** Creates an isolated in-memory service, suitable for tests or ephemeral servers. */
    public static StatisticService inMemory(EventBus eventBus) {
        return fromRepository(new InMemoryStatisticRepository(), eventBus);
    }

    /** Creates an in-memory service with explicit operational options. */
    public static StatisticService inMemory(EventBus eventBus, StatisticServiceOptions options) {
        return fromRepository(new InMemoryStatisticRepository(), eventBus, options);
    }

    /** Creates a service over a caller-owned repository. */
    public static StatisticService fromRepository(StatisticRepository repository, EventBus eventBus) {
        return fromRepository(repository, eventBus, StatisticServiceOptions.defaults());
    }

    /** Creates a service over a repository with explicit operational options. */
    public static StatisticService fromRepository(
            StatisticRepository repository, EventBus eventBus, StatisticServiceOptions options) {
        return fromRepository(repository, eventBus, options, Clock.systemUTC());
    }

    /** Creates a service with explicit operational options and clock. */
    public static StatisticService fromRepository(
            StatisticRepository repository, EventBus eventBus, StatisticServiceOptions options, Clock clock) {
        return DefaultStatisticService.create(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(eventBus, "eventBus"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }

    /** Creates a service backed by caller-owned Cotani SQL storage. */
    public static StatisticService storage(CotaniStorage storage, EventBus eventBus) {
        return fromRepository(new StorageStatisticRepository(storage), eventBus);
    }

    /** Creates a SQL-backed service with explicit operational options. */
    public static StatisticService storage(CotaniStorage storage, EventBus eventBus, StatisticServiceOptions options) {
        return fromRepository(new StorageStatisticRepository(storage), eventBus, options);
    }

    /** Returns the migrations required by the SQL adapter. */
    public static List<Migration> migrations() {
        return StorageStatisticRepository.migrations();
    }
}

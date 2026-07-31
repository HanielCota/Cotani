package com.cotani.cooldown;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.CooldownService;
import com.cotani.cooldown.api.DistributedCooldownService;
import com.cotani.cooldown.cache.PlayerCooldowns;
import com.cotani.cooldown.internal.DefaultCooldownService;
import com.cotani.cooldown.internal.InMemoryCooldownStore;
import com.cotani.cooldown.storage.AddCooldownLeaseTokenMigration;
import com.cotani.cooldown.storage.CreateCooldownsTableMigration;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CotaniCooldowns {

    private CotaniCooldowns() {}

    public static CooldownService inMemory() {
        return DefaultCooldownService.inMemory();
    }

    public static CooldownService inMemory(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new DefaultCooldownService(new InMemoryCooldownStore(), clock);
    }

    public static CooldownService cacheBacked(PlayerDataCache<PlayerCooldowns> playerCache) {
        Objects.requireNonNull(playerCache, "playerCache");
        return DefaultCooldownService.cacheBacked(playerCache);
    }

    /** Migrations required by {@link #distributed}. Register them before starting storage. */
    public static List<Migration> migrations() {
        return List.of(new CreateCooldownsTableMigration(), new AddCooldownLeaseTokenMigration());
    }

    public static DistributedCooldownService distributed(CotaniStorage storage, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(scheduler, "scheduler");
        return distributed(storage, scheduler, Clock.systemUTC(), Duration.ofMinutes(5));
    }

    public static DistributedCooldownService distributed(
            CotaniStorage storage, PaperTaskScheduler scheduler, Clock clock, Duration cleanupInterval) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(cleanupInterval, "cleanupInterval");
        return new SqlDistributedCooldownService(storage, scheduler, clock, cleanupInterval);
    }
}

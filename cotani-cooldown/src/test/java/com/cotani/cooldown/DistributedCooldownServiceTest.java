package com.cotani.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownTargets;
import com.cotani.cooldown.api.DistributedCooldownService;
import com.cotani.cooldown.internal.InMemoryCooldownStore;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DistributedCooldownServiceTest {

    @Test
    void twoServicesAtomicallyAcquireOneSharedCooldown(@TempDir Path directory) {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var scheduler = scheduler();
        var storage = startStorage(directory.resolve("cooldowns.db"), scheduler);
        try (DistributedCooldownService first =
                        CotaniCooldowns.distributed(storage, scheduler, clock, Duration.ofMinutes(1));
                DistributedCooldownService second =
                        CotaniCooldowns.distributed(storage, scheduler, clock, Duration.ofMinutes(1))) {
            var key = new CooldownKey(CooldownTargets.user(UUID.randomUUID()), CooldownAction.of("daily.reward"));
            var attempts =
                    new ArrayList<java.util.concurrent.CompletionStage<com.cotani.cooldown.api.CooldownResult>>();
            for (int i = 0; i < 64; i++) {
                attempts.add(
                        (i & 1) == 0
                                ? first.checkAndStartAsync(key, Duration.ofMinutes(5))
                                : second.checkAndStartAsync(key, Duration.ofMinutes(5)));
            }

            long allowed = attempts.stream()
                    .map(stage -> stage.toCompletableFuture().join())
                    .filter(com.cotani.cooldown.api.CooldownResult::allowed)
                    .count();

            assertEquals(1, allowed);
            assertEquals(1L, first.sizeAsync().toCompletableFuture().join());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void distributedCleanupRemovesExpiredRows(@TempDir Path directory) {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var scheduler = scheduler();
        var storage = startStorage(directory.resolve("cleanup.db"), scheduler);
        try (var service = CotaniCooldowns.distributed(storage, scheduler, clock, Duration.ofMinutes(1))) {
            for (int i = 0; i < 250; i++) {
                var key = new CooldownKey(CooldownTargets.resource("resource-" + i), CooldownAction.of("use"));
                assertTrue(service.checkAndStartAsync(key, Duration.ofSeconds(1))
                        .toCompletableFuture()
                        .join()
                        .allowed());
            }
            assertEquals(250L, service.sizeAsync().toCompletableFuture().join());

            clock.advance(Duration.ofMinutes(2));
            service.clearExpiredAsync().toCompletableFuture().join();

            assertEquals(0L, service.sizeAsync().toCompletableFuture().join());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void storedCooldownRoundTripsDelimitersInResourceAndAction(@TempDir Path directory) {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var scheduler = scheduler();
        var storage = startStorage(directory.resolve("delimiters.db"), scheduler);
        try (var service = CotaniCooldowns.distributed(storage, scheduler, clock, Duration.ofMinutes(1))) {
            var key = new CooldownKey(CooldownTargets.resource("world:spawn"), CooldownAction.of("kit:daily"));

            assertTrue(service.checkAndStartAsync(key, Duration.ofMinutes(5))
                    .toCompletableFuture()
                    .join()
                    .allowed());

            assertEquals(
                    key,
                    service.findAsync(key)
                            .toCompletableFuture()
                            .join()
                            .orElseThrow()
                            .key());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void inMemoryStorePerformsOpportunisticCleanupUnderKeyChurn() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var store = new InMemoryCooldownStore();
        for (int i = 0; i < 1_000; i++) {
            var key = new CooldownKey(CooldownTargets.resource("resource-" + i), CooldownAction.of("use"));
            store.checkAndStart(key, Duration.ofSeconds(1), clock);
        }
        clock.advance(Duration.ofMinutes(2));

        var liveKey = new CooldownKey(CooldownTargets.global(), CooldownAction.of("live"));
        store.checkAndStart(liveKey, Duration.ofMinutes(1), clock);

        assertEquals(1L, store.estimatedSize());
    }

    private static CotaniStorage startStorage(Path path, PaperTaskScheduler scheduler) {
        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(path)))
                .scheduler(scheduler)
                .migrations(CotaniCooldowns.migrations().toArray(Migration[]::new))
                .build();
        storage.startAsync().toCompletableFuture().join();
        return storage;
    }

    private static PaperTaskScheduler scheduler() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        when(scheduler.asyncExecutor()).thenReturn(Runnable::run);
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
        return scheduler;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

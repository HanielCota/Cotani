package com.cotani.reward.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.RewardClaimCommand;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardDefinition;
import com.cotani.reward.api.RewardOnCooldownException;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RewardStorageIntegrationTest {
    @Test
    void roundTripsClaimsAndPreservesAtomicState(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);
        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("reward.db"))))
                .scheduler(scheduler)
                .migrations(StorageRewardRepository.migrations().toArray(Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            var repository = new StorageRewardRepository(storage, clock);
            var player = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            var instant = clock.instant();
            var definition = new RewardDefinition(
                    com.cotani.reward.api.RewardId.of("daily"),
                    Duration.ofDays(1),
                    Duration.ofDays(2),
                    7,
                    List.of(new CurrencyGrant("coins", new BigDecimal("100.00"))));
            var firstCommand = new RewardClaimCommand(
                    new RewardClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                    player,
                    definition,
                    instant);

            var first =
                    repository.claimAsync(firstCommand).toCompletableFuture().join();
            assertEquals(
                    first,
                    repository.claimAsync(firstCommand).toCompletableFuture().join());
            assertEquals(0, ((CurrencyGrant) first.grants().getFirst()).amount().compareTo(new BigDecimal("100")));
            assertEquals(
                    1,
                    repository
                            .pendingClaimsAsync(10)
                            .toCompletableFuture()
                            .join()
                            .size());
            assertTrue(repository
                    .markSettledAsync(first.claimId())
                    .toCompletableFuture()
                    .join());
            assertTrue(repository
                    .pendingClaimsAsync(10)
                    .toCompletableFuture()
                    .join()
                    .isEmpty());

            clock.advance(Duration.ofSeconds(1));
            var cooldown = assertThrows(
                    CompletionException.class,
                    () -> repository
                            .claimAsync(new RewardClaimCommand(
                                    RewardClaimId.random(), player, definition, instant.plusSeconds(1)))
                            .toCompletableFuture()
                            .join());
            assertTrue(cooldown.getCause() instanceof RewardOnCooldownException);

            clock.advance(Duration.ofDays(1));
            var second = repository
                    .claimAsync(new RewardClaimCommand(
                            RewardClaimId.random(), player, definition, instant.plus(Duration.ofDays(1))))
                    .toCompletableFuture()
                    .join();
            assertEquals(2, second.streak());
            assertEquals(2, second.totalClaims());

            var concurrentPlayer = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
            var concurrentA = repository.claimAsync(
                    new RewardClaimCommand(RewardClaimId.random(), concurrentPlayer, definition, clock.instant()));
            var concurrentB = repository.claimAsync(
                    new RewardClaimCommand(RewardClaimId.random(), concurrentPlayer, definition, clock.instant()));
            var outcomes = List.of(concurrentA, concurrentB).stream()
                    .map(stage -> stage.handle((ignored, failure) -> failure == null)
                            .toCompletableFuture()
                            .join())
                    .toList();
            assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());

            var databaseClockRepository = new StorageRewardRepository(storage);
            var databaseTimedClaim = databaseClockRepository
                    .claimAsync(new RewardClaimCommand(
                            RewardClaimId.random(),
                            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                            definition,
                            Instant.EPOCH))
                    .toCompletableFuture()
                    .join();
            assertTrue(databaseTimedClaim.claimedAt().isAfter(Instant.now().minusSeconds(30)));
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
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
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}

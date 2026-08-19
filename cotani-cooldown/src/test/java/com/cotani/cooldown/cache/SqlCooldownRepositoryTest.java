package com.cotani.cooldown.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cooldown.CotaniCooldowns;
import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownTargets;
import com.cotani.cooldown.api.UserCooldownTarget;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlCooldownRepositoryTest {
    @TempDir
    Path directory;

    private CotaniStorage storage;
    private SqlCooldownRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        var scheduler = scheduler();
        storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("cooldowns.db"))))
                .scheduler(scheduler)
                .migrations(CotaniCooldowns.migrations().toArray(Migration[]::new))
                .build();
        storage.startAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        repository = new SqlCooldownRepository(storage);
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldReturnEmptyForUnknownPlayer() throws Exception {
        var result = repository.find(UUID.randomUUID()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRoundTripSavedCooldowns() throws Exception {
        var playerId = UUID.randomUUID();
        var startedAt = Instant.now().minusSeconds(10);
        var playerCooldowns = new PlayerCooldowns(playerId);
        var firstKey = new CooldownKey(new UserCooldownTarget(playerId), CooldownAction.of("use"));
        var secondKey = new CooldownKey(new UserCooldownTarget(playerId), CooldownAction.of("other"));
        playerCooldowns.put(new CooldownEntry(firstKey, startedAt, startedAt.plusSeconds(60)));
        playerCooldowns.put(new CooldownEntry(secondKey, startedAt, startedAt.plusSeconds(120)));

        repository.save(playerId, playerCooldowns).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var loaded = repository
                .find(playerId)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(playerId, loaded.playerId());
        assertEquals(2, loaded.activeCooldowns().size());
        assertEquals(
                playerCooldowns.find("use").orElseThrow(), loaded.find("use").orElseThrow());
        assertEquals(
                playerCooldowns.find("other").orElseThrow(),
                loaded.find("other").orElseThrow());
    }

    @Test
    void shouldSkipExpiredEntriesWhenSaving() throws Exception {
        var playerId = UUID.randomUUID();
        var playerCooldowns = new PlayerCooldowns(playerId);
        var key = new CooldownKey(new UserCooldownTarget(playerId), CooldownAction.of("use"));
        playerCooldowns.put(new CooldownEntry(
                key, Instant.now().minusSeconds(10), Instant.now().minusSeconds(1)));

        repository.save(playerId, playerCooldowns).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var loaded = repository.find(playerId).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void shouldFilterExpiredEntriesWhenLoading() throws Exception {
        var playerId = UUID.randomUUID();
        var now = Instant.now();
        storage.queryExecutor()
                .update(
                        "INSERT INTO cotani_cooldowns (cooldown_id, target_type, target_id, action_name, started_at, expires_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        binder -> binder.string("USER:" + playerId + ":use")
                                .string("USER")
                                .string(playerId.toString())
                                .string("use")
                                .instant(now.minusSeconds(10))
                                .instant(now.minusSeconds(5)))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        var loaded = repository.find(playerId).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void shouldDeleteAllEntriesForPlayer() throws Exception {
        var playerId = UUID.randomUUID();
        var playerCooldowns = new PlayerCooldowns(playerId);
        var startedAt = Instant.now();
        playerCooldowns.put(new CooldownEntry(
                new CooldownKey(new UserCooldownTarget(playerId), CooldownAction.of("use")),
                startedAt,
                startedAt.plusSeconds(60)));
        repository.save(playerId, playerCooldowns).toCompletableFuture().get(5, TimeUnit.SECONDS);

        repository.delete(playerId).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var loaded = repository.find(playerId).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void shouldReplaceEntriesOnSave() throws Exception {
        var playerId = UUID.randomUUID();
        var startedAt = Instant.now();
        var key = new CooldownKey(new UserCooldownTarget(playerId), CooldownAction.of("use"));
        var first = new PlayerCooldowns(playerId);
        first.put(new CooldownEntry(key, startedAt, startedAt.plusSeconds(30)));
        var second = new PlayerCooldowns(playerId);
        second.put(new CooldownEntry(key, startedAt, startedAt.plusSeconds(300)));

        repository.save(playerId, first).toCompletableFuture().get(5, TimeUnit.SECONDS);
        repository.save(playerId, second).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var loaded = repository
                .find(playerId)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(1, loaded.activeCooldowns().size());
        assertEquals(
                startedAt.plusSeconds(300), loaded.find("use").orElseThrow().expiresAt());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class, () -> repository.find(null));
        assertThrows(NullPointerException.class, () -> repository.save(null, new PlayerCooldowns(UUID.randomUUID())));
        assertThrows(NullPointerException.class, () -> repository.save(UUID.randomUUID(), null));
        assertThrows(NullPointerException.class, () -> repository.delete(null));
    }

    @Test
    void shouldNotMixPlayers() throws Exception {
        var firstPlayer = UUID.randomUUID();
        var secondPlayer = UUID.randomUUID();
        var startedAt = Instant.now();
        var key = new CooldownKey(new UserCooldownTarget(firstPlayer), CooldownAction.of("use"));
        var first = new PlayerCooldowns(firstPlayer);
        first.put(new CooldownEntry(key, startedAt, startedAt.plusSeconds(60)));

        repository.save(firstPlayer, first).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(repository
                .find(secondPlayer)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .isEmpty());
    }

    @Test
    void shouldRoundTripActionNamesWithDelimiters() throws Exception {
        var playerId = UUID.randomUUID();
        var startedAt = Instant.now();
        var playerCooldowns = new PlayerCooldowns(playerId);
        var entry = new CooldownEntry(
                new CooldownKey(CooldownTargets.user(playerId), CooldownAction.of("kit:daily")),
                startedAt,
                startedAt.plusSeconds(60));
        playerCooldowns.put(entry);

        repository.save(playerId, playerCooldowns).toCompletableFuture().get(5, TimeUnit.SECONDS);

        var loaded = repository
                .find(playerId)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(entry, loaded.find("kit:daily").orElseThrow());
    }

    private static PaperTaskScheduler scheduler() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        when(scheduler.asyncExecutor()).thenReturn(Runnable::run);
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());

        return scheduler;
    }
}

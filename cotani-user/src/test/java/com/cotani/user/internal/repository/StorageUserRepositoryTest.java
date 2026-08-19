package com.cotani.user.internal.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.error.StorageException;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.user.internal.mapper.UserMapper;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway")
class StorageUserRepositoryTest {
    @TempDir
    Path tempDir;

    private final Plugin plugin = mock(Plugin.class);
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    private CotaniStorage storage;
    private StorageUserRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        when(scheduler.asyncExecutor()).thenReturn(Runnable::run);
        when(scheduler.asyncTimer(any(), any(Duration.class), any(Duration.class)))
                .thenReturn(SchedulerTask.noop());

        SQLiteCredentials credentials = mock(SQLiteCredentials.class);
        when(credentials.path()).thenReturn(tempDir.resolve("in-memory-placeholder.db"));
        when(credentials.jdbcUrl()).thenReturn("jdbc:sqlite::memory:");

        storage = CotaniStorage.create(plugin)
                .backend(new SQLiteBackend(credentials))
                .scheduler(scheduler)
                .migrations(new CreateUsersTableMigration())
                .build();
        storage.startAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
        repository = new StorageUserRepository(storage, new UserMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void findReturnsEmptyForUnknownUser() throws Exception {
        Optional<SimpleCotaniUser> result = repository
                .find(UUID.randomUUID(), "Steve")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertTrue(result.isEmpty());
    }

    @Test
    void saveInsertsUserAndFindRoundTripsAllFields() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = new SimpleCotaniUser(uniqueId, UUID.randomUUID(), "Steve", 1_000L, 2_000L, 3_000L, 4L);

        repository.save(user).toCompletableFuture().get(5, TimeUnit.SECONDS);

        SimpleCotaniUser loaded = repository
                .find(uniqueId, "Steve")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals(uniqueId, loaded.uniqueId());
        assertEquals("Steve", loaded.username());
        assertEquals(1_000L, loaded.firstJoinAt());
        assertEquals(2_000L, loaded.lastJoinAt());
        assertEquals(3_000L, loaded.lastQuitAt());
        assertEquals(4L, loaded.version());
    }

    @Test
    void saveUpsertsExistingUserWithoutDuplicatingRows() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser first = new SimpleCotaniUser(uniqueId, UUID.randomUUID(), "Steve", 1_000L, 2_000L, 3_000L, 4L);
        SimpleCotaniUser second = new SimpleCotaniUser(uniqueId, UUID.randomUUID(), "Alex", 5_000L, 6_000L, 7_000L, 8L);

        repository.save(first).toCompletableFuture().get(5, TimeUnit.SECONDS);
        repository.save(second).toCompletableFuture().get(5, TimeUnit.SECONDS);

        SimpleCotaniUser loaded = repository
                .find(uniqueId, "Alex")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals("Alex", loaded.username());
        assertEquals(8L, loaded.version());
        assertEquals(1_000L, loaded.firstJoinAt());

        Long rowCount = storage
                .queryExecutor()
                .queryMany(
                        "SELECT unique_id FROM cotani_users WHERE unique_id = ?",
                        binder -> binder.string(uniqueId.toString()),
                        row -> row.getString("unique_id"))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .stream()
                .count();
        assertEquals(1L, rowCount);
    }

    @Test
    void findUsesFallbackUsernameWhenStoredUsernameIsBlank() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        storage.queryExecutor()
                .update(
                        "INSERT INTO cotani_users (unique_id, username, first_join_at, last_join_at, last_quit_at, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        binder -> binder.string(uniqueId.toString())
                                .string("   ")
                                .longValue(1L)
                                .longValue(2L)
                                .longValue(3L)
                                .longValue(0L))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        SimpleCotaniUser loaded = repository
                .find(uniqueId, "Steve")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();

        assertEquals("Steve", loaded.username());
    }

    @Test
    void findByUniqueIdReturnsUnknownUsernameFallback() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        storage.queryExecutor()
                .update(
                        "INSERT INTO cotani_users (unique_id, username, first_join_at, last_join_at, last_quit_at, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        binder -> binder.string(uniqueId.toString())
                                .string("   ")
                                .longValue(1L)
                                .longValue(2L)
                                .longValue(3L)
                                .longValue(0L))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        SimpleCotaniUser loaded = repository
                .findByUniqueId(uniqueId)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();

        assertEquals("unknown", loaded.username());
    }

    @Test
    void saveAllPersistsMultipleUsers() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        SimpleCotaniUser first = new SimpleCotaniUser(firstId, UUID.randomUUID(), "Steve", 1_000L, 2_000L, 3_000L, 4L);
        SimpleCotaniUser second = new SimpleCotaniUser(secondId, UUID.randomUUID(), "Alex", 5_000L, 6_000L, 7_000L, 8L);

        repository.saveAll(List.of(first, second)).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(
                firstId,
                repository
                        .find(firstId, "Steve")
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS)
                        .orElseThrow()
                        .uniqueId());
        assertEquals(
                "Alex",
                repository
                        .find(secondId, "Alex")
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS)
                        .orElseThrow()
                        .username());
    }

    @Test
    void saveAllWithEmptyCollectionCompletesImmediately() throws Exception {
        repository.saveAll(List.of()).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void saveAllUpsertsExistingUsersInBatch() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser original =
                new SimpleCotaniUser(uniqueId, UUID.randomUUID(), "Steve", 1_000L, 2_000L, 3_000L, 1L);
        repository.save(original).toCompletableFuture().get(5, TimeUnit.SECONDS);

        SimpleCotaniUser updated =
                new SimpleCotaniUser(uniqueId, UUID.randomUUID(), "Alex", 1_000L, 9_000L, 3_000L, 2L);
        repository.saveAll(List.of(updated)).toCompletableFuture().get(5, TimeUnit.SECONDS);

        SimpleCotaniUser loaded = repository
                .find(uniqueId, "Alex")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        assertEquals("Alex", loaded.username());
        assertEquals(9_000L, loaded.lastJoinAt());
        assertEquals(2L, loaded.version());
    }

    @Test
    void findReturnsEmptyAfterUserIsDeleted() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = new SimpleCotaniUser(uniqueId, UUID.randomUUID(), "Steve", 1_000L, 2_000L, 3_000L, 0L);
        repository.save(user).toCompletableFuture().get(5, TimeUnit.SECONDS);

        storage.queryExecutor()
                .update("DELETE FROM cotani_users WHERE unique_id = ?", binder -> binder.string(uniqueId.toString()))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertTrue(repository
                .find(uniqueId, "Steve")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .isEmpty());
    }

    @Test
    void savePropagatesStorageFailure() throws Exception {
        storage.queryExecutor()
                .update("DROP TABLE cotani_users", binder -> {})
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UUID.randomUUID(), "Steve", 1_000L);

        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> repository.save(user).toCompletableFuture().get(5, TimeUnit.SECONDS));

        assertInstanceOf(StorageException.class, failure.getCause());
    }

    @Test
    void findPropagatesStorageFailure() throws Exception {
        storage.queryExecutor()
                .update("DROP TABLE cotani_users", binder -> {})
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> repository
                        .find(UUID.randomUUID(), "Steve")
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS));

        assertInstanceOf(StorageException.class, failure.getCause());
    }

    @Test
    void saveRejectsNullUser() {
        assertThrows(NullPointerException.class, () -> repository.save(null));
    }

    @Test
    void saveAllRejectsNullCollection() {
        assertThrows(NullPointerException.class, () -> repository.saveAll(null));
    }

    @Test
    void loadedUsersReceiveFreshSessionIds() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1_000L);
        repository.save(user).toCompletableFuture().get(5, TimeUnit.SECONDS);

        SimpleCotaniUser firstLoad = repository
                .find(uniqueId, "Steve")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();
        SimpleCotaniUser secondLoad = repository
                .find(uniqueId, "Steve")
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS)
                .orElseThrow();

        assertNotNull(firstLoad.sessionId());
        assertTrue(!firstLoad.sessionId().equals(secondLoad.sessionId()));
    }
}

package com.cotani.storage.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.repository.CotaniRepository;
import com.cotani.storage.schema.Schema;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class CotaniStorageTest {
    @TempDir
    Path tempDir;

    private @Nullable CotaniStorage currentStorage;

    @AfterEach
    void tearDown() {
        if (currentStorage != null) {
            currentStorage.closeAsync().toCompletableFuture().join();
            currentStorage = null;
        }
    }

    private static Plugin mockPlugin() {
        return Mockito.mock(Plugin.class);
    }

    private static PaperTaskScheduler mockScheduler() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        Executor executor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(executor);

        return scheduler;
    }

    private CotaniStorage newStorage(PaperTaskScheduler scheduler) {
        Path dbFile = tempDir.resolve("test.db");
        currentStorage = CotaniStorage.create(mockPlugin())
                .backend(new SQLiteBackend(new SQLiteCredentials(dbFile)))
                .scheduler(scheduler)
                .repositories(TestRepository.class)
                .build();
        return currentStorage;
    }

    @Test
    void buildFailsWithoutScheduler() {
        var builder = CotaniStorage.create(mockPlugin())
                .backend(new SQLiteBackend(new SQLiteCredentials(tempDir.resolve("test.db"))));

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void buildFailsWithoutBackend() {
        var builder = CotaniStorage.create(mockPlugin()).scheduler(mockScheduler());

        assertThrows(NullPointerException.class, builder::build);
    }

    @Test
    void startAsyncCompletesWithoutFailure() {
        var storage = newStorage(mockScheduler());

        assertDoesNotThrow(() -> storage.startAsync().toCompletableFuture().join());
    }

    @Test
    void startAsyncIsIdempotent() {
        var storage = newStorage(mockScheduler());

        var first = storage.startAsync().toCompletableFuture().join();
        var second = storage.startAsync().toCompletableFuture().join();

        assertSame(first, second);
    }

    @Test
    void concurrentStartCallsShareTheSameStartup() {
        var migrationGate = new CompletableFuture<Void>();
        Migration migration = new Migration() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public String description() {
                return "controlled";
            }

            @Override
            public CompletionStage<Void> migrate(Schema schema) {
                return migrationGate;
            }
        };
        var storage = CotaniStorage.create(mockPlugin())
                .backend(new SQLiteBackend(new SQLiteCredentials(tempDir.resolve("controlled.db"))))
                .scheduler(mockScheduler())
                .migrations(migration)
                .build();
        currentStorage = storage;

        var first = storage.startAsync();
        var second = storage.startAsync();

        assertSame(first, second);
        assertFalse(first.toCompletableFuture().isDone());
        migrationGate.complete(null);
        assertSame(storage, first.toCompletableFuture().join());
    }

    @Test
    void failedRepositoryRegistrationIsTerminalAndCloseRemainsIdempotent() {
        var storage = CotaniStorage.create(mockPlugin())
                .backend(new SQLiteBackend(new SQLiteCredentials(tempDir.resolve("broken-repository.db"))))
                .scheduler(mockScheduler())
                .repositories(BrokenRepository.class)
                .build();
        currentStorage = storage;

        var first = storage.startAsync();
        var second = storage.startAsync();

        assertSame(first, second);
        assertThrows(
                CompletionException.class, () -> first.toCompletableFuture().join());
        assertDoesNotThrow(() -> storage.closeAsync().toCompletableFuture().join());
        assertDoesNotThrow(() -> storage.closeAsync().toCompletableFuture().join());
    }

    @Test
    void migrationFailureClosesSQLiteProvider() throws Exception {
        var dbFile = tempDir.resolve("failed-migration.db");
        Migration migration = new Migration() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public String description() {
                return "fails";
            }

            @Override
            public CompletionStage<Void> migrate(Schema schema) {
                return CompletableFuture.failedFuture(new IllegalStateException("migration failed"));
            }
        };
        var storage = CotaniStorage.create(mockPlugin())
                .backend(new SQLiteBackend(new SQLiteCredentials(dbFile)))
                .scheduler(mockScheduler())
                .migrations(migration)
                .build();
        currentStorage = storage;

        assertThrows(
                CompletionException.class,
                () -> storage.startAsync().toCompletableFuture().join());

        Files.delete(dbFile);
        assertFalse(Files.exists(dbFile));
    }

    @Test
    void repositoryReturnsRegisteredInstance() {
        var storage = newStorage(mockScheduler());

        storage.startAsync().toCompletableFuture().join();

        assertNotNull(storage.repository(TestRepository.class));
    }

    @Test
    void repositoryThrowsForUnregisteredType() {
        var storage = newStorage(mockScheduler());

        storage.startAsync().toCompletableFuture().join();

        assertThrows(IllegalStateException.class, () -> storage.repository(UnknownRepository.class));
    }

    @Test
    void closeAsyncCompletesWithoutFailure() {
        var scheduler = mockScheduler();
        var storage = newStorage(scheduler);
        storage.startAsync().toCompletableFuture().join();

        assertDoesNotThrow(() -> storage.closeAsync().toCompletableFuture().join());
    }

    @Test
    void closeAsyncIsIdempotentAndRejectsLaterQueries() {
        var storage = newStorage(mockScheduler());
        storage.startAsync().toCompletableFuture().join();

        var first = storage.closeAsync();
        var second = storage.closeAsync();

        assertSame(first, second);
        assertDoesNotThrow(() -> first.toCompletableFuture().join());
        var rejected = storage.queryExecutor().update("SELECT 1", binder -> {});
        var failure = assertThrows(
                CompletionException.class, () -> rejected.toCompletableFuture().join());
        assertInstanceOf(RejectedExecutionException.class, failure.getCause());
    }

    public static final class TestRepository extends CotaniRepository {
        public TestRepository(CotaniStorage storage) {
            super(storage);
        }
    }

    public static final class UnknownRepository extends CotaniRepository {
        public UnknownRepository(CotaniStorage storage) {
            super(storage);
        }
    }

    public static final class BrokenRepository extends CotaniRepository {
        public BrokenRepository(CotaniStorage storage) {
            super(storage);
            throw new IllegalStateException("broken repository");
        }
    }
}

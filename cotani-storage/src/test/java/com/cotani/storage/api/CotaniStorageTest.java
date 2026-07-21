package com.cotani.storage.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.repository.CotaniRepository;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
        Executor executor = Executors.newSingleThreadExecutor();
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
}

package com.cotani.location.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.location.api.Home;
import com.cotani.location.api.HomeId;
import com.cotani.location.api.LocationName;
import com.cotani.location.api.LocationPosition;
import com.cotani.location.api.LocationSnapshot;
import com.cotani.location.api.Warp;
import com.cotani.location.api.WarpId;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.backend.SQLiteBackend;
import com.cotani.storage.backend.SQLiteCredentials;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocationStorageIntegrationTest {
    @Test
    void roundTripsHomesAndWarpsWithSQLite(@TempDir Path directory) {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        Executor directExecutor = Runnable::run;
        when(scheduler.asyncExecutor()).thenReturn(directExecutor);

        var storage = CotaniStorage.create(mock(Plugin.class))
                .backend(new SQLiteBackend(new SQLiteCredentials(directory.resolve("locations.db"))))
                .scheduler(scheduler)
                .migrations(StorageLocationRepository.migrations().toArray(Migration[]::new))
                .build();
        try {
            storage.startAsync().toCompletableFuture().join();
            var repository = new StorageLocationRepository(storage);
            var now = Instant.parse("2026-01-01T00:00:00Z");
            var position = new LocationPosition(
                    UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 10.5, 64, -2.5, 90.0f, 0.0f);
            var home = new Home(
                    new HomeId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), LocationName.of("base")),
                    position,
                    now,
                    now);
            var warp = new Warp(new WarpId(LocationName.of("spawn")), position, now, now);

            var secondHome = new Home(new HomeId(home.id().ownerId(), LocationName.of("zulu")), position, now, now);
            var updatedHome = new Home(home.id(), position, home.createdAt(), now.plusSeconds(1));

            repository.saveHomeAsync(home).toCompletableFuture().join();
            repository.saveHomeAsync(secondHome).toCompletableFuture().join();
            repository.saveWarpAsync(warp).toCompletableFuture().join();
            repository.saveHomeAsync(updatedHome).toCompletableFuture().join();

            assertEquals(
                    new LocationSnapshot(List.of(updatedHome, secondHome), List.of(warp)),
                    repository.loadAsync().toCompletableFuture().join());
        } finally {
            storage.closeAsync().toCompletableFuture().join();
        }
    }
}

package com.cotani.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.location.api.Home;
import com.cotani.location.api.HomeId;
import com.cotani.location.api.HomeLimitExceededException;
import com.cotani.location.api.HomeNotFoundException;
import com.cotani.location.api.LocationName;
import com.cotani.location.api.LocationPosition;
import com.cotani.location.api.LocationRepository;
import com.cotani.location.api.LocationService;
import com.cotani.location.api.LocationServiceOptions;
import com.cotani.location.api.LocationSnapshot;
import com.cotani.location.api.Warp;
import com.cotani.location.api.WarpId;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocationServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final LocationPosition POSITION =
            new LocationPosition(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 12.5, 64, -4.5, 90.0f, 0.0f);

    @Test
    void normalizesNamesAndRejectsInvalidNames() {
        assertEquals("my-home", LocationName.of(" My-Home ").value());
        assertThrows(IllegalArgumentException.class, () -> LocationName.of("not a name"));
        assertThrows(IllegalArgumentException.class, () -> LocationName.of(""));
    }

    @Test
    void setsFindsListsAndDeletesHomesAndWarps() {
        LocationService service = CotaniLocations.inMemory();

        var home = service.setHomeAsync(PLAYER_ID, LocationName.of("base"), POSITION)
                .toCompletableFuture()
                .join();
        var warp = service.setWarpAsync(LocationName.of("spawn"), POSITION)
                .toCompletableFuture()
                .join();

        assertEquals(
                home,
                service.findHomeAsync(PLAYER_ID, LocationName.of("BASE"))
                        .toCompletableFuture()
                        .join()
                        .orElseThrow());
        assertEquals(
                warp,
                service.findWarpAsync(LocationName.of("SPAWN"))
                        .toCompletableFuture()
                        .join()
                        .orElseThrow());
        assertEquals(
                List.of(home),
                service.homesAsync(PLAYER_ID).toCompletableFuture().join());
        assertEquals(List.of(warp), service.warpsAsync().toCompletableFuture().join());

        service.deleteHomeAsync(PLAYER_ID, LocationName.of("base"))
                .toCompletableFuture()
                .join();
        service.deleteWarpAsync(LocationName.of("spawn")).toCompletableFuture().join();

        assertTrue(service.findHomeAsync(PLAYER_ID, LocationName.of("base"))
                .toCompletableFuture()
                .join()
                .isEmpty());
        assertTrue(service.findWarpAsync(LocationName.of("spawn"))
                .toCompletableFuture()
                .join()
                .isEmpty());
        var missingHome = assertThrows(
                CompletionException.class,
                () -> service.deleteHomeAsync(PLAYER_ID, LocationName.of("base"))
                        .toCompletableFuture()
                        .join());
        assertTrue(missingHome.getCause() instanceof HomeNotFoundException);
    }

    @Test
    void enforcesHomeLimitOnlyForNewHomes() {
        LocationService service = CotaniLocations.fromRepositoryAsync(
                        new ImmediateRepository(), new LocationServiceOptions(1, Duration.ofSeconds(1)))
                .toCompletableFuture()
                .join();

        service.setHomeAsync(PLAYER_ID, LocationName.of("base"), POSITION)
                .toCompletableFuture()
                .join();
        var limitFailure = assertThrows(
                CompletionException.class,
                () -> service.setHomeAsync(PLAYER_ID, LocationName.of("mine"), POSITION)
                        .toCompletableFuture()
                        .join());
        assertTrue(limitFailure.getCause() instanceof HomeLimitExceededException);

        assertTrue(service.setHomeAsync(PLAYER_ID, LocationName.of("base"), POSITION)
                .toCompletableFuture()
                .join()
                .id()
                .name()
                .value()
                .equals("base"));
    }

    @Test
    void persistsBeforeReplacingVisibleState() {
        var repository = new BlockingRepository();
        LocationService service = CotaniLocations.fromRepositoryAsync(repository)
                .toCompletableFuture()
                .join();

        var save = service.setHomeAsync(PLAYER_ID, LocationName.of("base"), POSITION);

        assertTrue(service.findHomeAsync(PLAYER_ID, LocationName.of("base"))
                .toCompletableFuture()
                .join()
                .isEmpty());
        assertFalse(save.toCompletableFuture().isDone());

        repository.completeSave();
        assertEquals("base", save.toCompletableFuture().join().id().name().value());
        assertTrue(service.findHomeAsync(PLAYER_ID, LocationName.of("base"))
                .toCompletableFuture()
                .join()
                .isPresent());
    }

    @Test
    void timeoutDoesNotReleaseQueueOrCloseBeforeDurableWriteCompletes() {
        var repository = new BlockingRepository();
        var service = CotaniLocations.fromRepositoryAsync(
                        repository, new LocationServiceOptions(3, Duration.ofMillis(5)))
                .toCompletableFuture()
                .join();

        var first = service.setHomeAsync(PLAYER_ID, LocationName.of("base"), POSITION);
        assertThrows(
                CompletionException.class, () -> first.toCompletableFuture().join());

        var second = service.setWarpAsync(LocationName.of("spawn"), POSITION);
        var close = service.closeAsync();
        assertEquals(0, repository.warpSaves.get());
        assertFalse(second.toCompletableFuture().isDone());
        assertFalse(close.toCompletableFuture().isDone());

        repository.completeSave();

        assertEquals("spawn", second.toCompletableFuture().join().id().name().value());
        close.toCompletableFuture().join();
        assertEquals(1, repository.warpSaves.get());
    }

    @Test
    void closePropagatesAcceptedMutationFailure() {
        LocationService service = CotaniLocations.fromRepositoryAsync(new FailingRepository())
                .toCompletableFuture()
                .join();

        var save = service.setWarpAsync(LocationName.of("spawn"), POSITION);
        assertThrows(CompletionException.class, () -> save.toCompletableFuture().join());

        var closeFailure = assertThrows(
                CompletionException.class,
                () -> service.closeAsync().toCompletableFuture().join());
        assertTrue(closeFailure.getCause() instanceof IllegalStateException);
    }

    private static final class ImmediateRepository implements LocationRepository {
        @Override
        public CompletionStage<LocationSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(LocationSnapshot.empty());
        }

        @Override
        public CompletionStage<Void> saveHomeAsync(Home home) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteHomeAsync(HomeId id) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> saveWarpAsync(Warp warp) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteWarpAsync(WarpId id) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class BlockingRepository implements LocationRepository {
        private final CompletableFuture<Void> save = new CompletableFuture<>();
        private final AtomicReference<LocationSnapshot> snapshot = new AtomicReference<>(LocationSnapshot.empty());
        private final AtomicInteger warpSaves = new AtomicInteger();

        @Override
        public CompletionStage<LocationSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(snapshot.get());
        }

        @Override
        public CompletionStage<Void> saveHomeAsync(Home home) {
            snapshot.set(new LocationSnapshot(List.of(home), List.of()));
            return save;
        }

        @Override
        public CompletionStage<Void> deleteHomeAsync(HomeId id) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> saveWarpAsync(Warp warp) {
            warpSaves.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteWarpAsync(WarpId id) {
            return CompletableFuture.completedFuture(null);
        }

        private void completeSave() {
            save.complete(null);
        }
    }

    private static final class FailingRepository implements LocationRepository {
        @Override
        public CompletionStage<LocationSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(LocationSnapshot.empty());
        }

        @Override
        public CompletionStage<Void> saveHomeAsync(Home home) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteHomeAsync(HomeId id) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> saveWarpAsync(Warp warp) {
            return CompletableFuture.failedFuture(new IllegalStateException("write failed"));
        }

        @Override
        public CompletionStage<Void> deleteWarpAsync(WarpId id) {
            return CompletableFuture.completedFuture(null);
        }
    }
}

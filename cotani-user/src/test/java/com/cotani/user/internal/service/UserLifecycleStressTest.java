package com.cotani.user.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.testkit.StressTestSupport;
import com.cotani.user.internal.cache.UserCache;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class UserLifecycleStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void oneThousandPlayersLoadSaveQuitAndReconnectWithoutReusingSessions() {
        var repository = new MemoryRepository();
        var service = new SimpleUserService(new UserCache(2_000), repository);
        int players = StressTestSupport.MINIMUM_ITERATIONS;
        var playerIds = IntStream.range(0, players)
                .mapToObj(index -> new UUID(0x75736572L, index + 1L))
                .toList();

        var firstSessions = StressTestSupport.concurrent(
                "user",
                "load-first-session",
                players,
                32,
                TIMEOUT,
                index -> service.load(playerIds.get(index), "Player" + index));
        assertEquals(players, firstSessions.size());
        assertTrue(playerIds.stream().allMatch(service::isLoaded));

        service.saveAll()
                .toCompletableFuture()
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .join();
        StressTestSupport.concurrent(
                "user",
                "quit",
                players,
                32,
                TIMEOUT,
                index -> service.unload(playerIds.get(index)).thenApply(_ -> Boolean.TRUE));
        assertTrue(playerIds.stream().noneMatch(service::isLoaded));

        var reconnected = StressTestSupport.concurrent(
                "user",
                "reconnect",
                players,
                32,
                TIMEOUT,
                index -> service.load(playerIds.get(index), "Reconnected" + index));
        for (int index = 0; index < players; index++) {
            assertEquals(playerIds.get(index), reconnected.get(index).uniqueId());
            assertNotEquals(
                    firstSessions.get(index).sessionId(), reconnected.get(index).sessionId());
            assertEquals("Reconnected" + index, reconnected.get(index).username());
        }
        assertEquals(players, repository.values.size());
    }

    @Test
    void oneThousandPendingLoadsForOneUuidShareOneRepositoryRead() {
        var repository = new GatedRepository();
        var service = new SimpleUserService(new UserCache(), repository);
        var playerId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        var loads = IntStream.range(0, StressTestSupport.MINIMUM_ITERATIONS)
                .mapToObj(_ -> service.load(playerId, "SharedPlayer").toCompletableFuture())
                .toList();

        assertEquals(1, repository.reads.get());
        assertTrue(loads.stream().noneMatch(CompletableFuture::isDone));
        repository.result.complete(Optional.empty());
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .join();

        var loaded = loads.getFirst().join();
        loads.forEach(result -> assertSame(loaded, result.join()));
        assertTrue(service.isLoaded(playerId));
    }

    private static class MemoryRepository implements UserRepository {
        private final ConcurrentHashMap<UUID, SimpleCotaniUser> values = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Optional<SimpleCotaniUser>> find(UUID uniqueId, String username) {
            return CompletableFuture.completedFuture(Optional.ofNullable(values.get(uniqueId)));
        }

        @Override
        public CompletionStage<Optional<SimpleCotaniUser>> findByUniqueId(UUID uniqueId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(values.get(uniqueId)));
        }

        @Override
        public CompletionStage<Optional<SimpleCotaniUser>> findByUsername(String username) {
            return CompletableFuture.completedFuture(values.values().stream()
                    .filter(user -> user.username().equalsIgnoreCase(username))
                    .findFirst());
        }

        @Override
        public CompletionStage<Void> save(SimpleCotaniUser user) {
            values.put(user.uniqueId(), user);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> saveAll(Collection<SimpleCotaniUser> users) {
            users.forEach(user -> values.put(user.uniqueId(), user));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class GatedRepository extends MemoryRepository {
        private final AtomicInteger reads = new AtomicInteger();
        private final CompletableFuture<Optional<SimpleCotaniUser>> result = new CompletableFuture<>();

        @Override
        public CompletionStage<Optional<SimpleCotaniUser>> find(UUID uniqueId, String username) {
            reads.incrementAndGet();
            return result;
        }
    }
}

package com.cotani.user.internal.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.api.CotaniUser;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class UserCacheTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
    private final UserCache cache = new UserCache(repository, scheduler);

    @Test
    void putAndFindReturnUser() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);

        cache.put(user);

        Optional<SimpleCotaniUser> internal = cache.findInternal(uniqueId);
        Optional<CotaniUser> exposed = cache.find(uniqueId);

        assertTrue(internal.isPresent());
        assertEquals(user.uniqueId(), internal.get().uniqueId());
        assertTrue(cache.contains(uniqueId));
        assertTrue(exposed.isPresent());
        assertEquals(user.uniqueId(), exposed.get().uniqueId());
    }

    @Test
    void removeOnlyWithMatchingSessionId() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        cache.put(user);

        assertFalse(cache.remove(uniqueId, UUID.randomUUID()));
        assertTrue(cache.contains(uniqueId));

        assertTrue(cache.remove(uniqueId, user.sessionId()));
        assertFalse(cache.contains(uniqueId));
    }

    @Test
    void saveIncrementsVersionAndUpdatesCachedValue() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1L).withVersion(3L);
        cache.put(user);

        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(CompletableFuture.completedFuture(null));

        cache.save(uniqueId).toCompletableFuture().join();

        verify(repository).save(argThat(u -> u.version() == 4L));
        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        assertEquals(4L, current.version());
    }

    @Test
    void saveDoesNotDowngradeVersionIfValueChangedDuringSave() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser original =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1L).withVersion(3L);
        cache.put(original);

        SimpleCotaniUser newer = original.withVersion(7L);
        cache.put(newer);

        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(CompletableFuture.completedFuture(null));

        cache.save(uniqueId).toCompletableFuture().join();

        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        assertEquals(8L, current.version());
    }

    @Test
    void saveAllSavesAllCachedUsers() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        cache.put(SimpleCotaniUser.createNew(firstId, "A", 1L).withVersion(1L));
        cache.put(SimpleCotaniUser.createNew(secondId, "B", 1L).withVersion(2L));

        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(CompletableFuture.completedFuture(null));

        cache.saveAll().toCompletableFuture().join();

        verify(repository, times(2)).save(any(SimpleCotaniUser.class));
    }

    @Test
    void clearRemovesAllEntries() {
        cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "A", 1L));
        cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "B", 1L));

        cache.clear();

        assertTrue(cache.allInternal().isEmpty());
    }
}

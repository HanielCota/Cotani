package com.cotani.user.internal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.user.api.CotaniUser;
import com.cotani.user.api.UserNotLoadedException;
import com.cotani.user.internal.cache.UserCache;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class SimpleUserServiceTest {

    private final UserCache cache = mock(UserCache.class);
    private final UserRepository repository = mock(UserRepository.class);
    private final SimpleUserService service = new SimpleUserService(cache, repository);

    @Test
    void loadCreatesNewUserWhenNotFound() {
        UUID uniqueId = UUID.randomUUID();
        String username = "Steve";

        when(repository.find(uniqueId, username)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        SimpleCotaniUser user =
                service.load(uniqueId, username).toCompletableFuture().join();

        assertEquals(uniqueId, user.uniqueId());
        assertEquals(username, user.username());
        assertEquals(0L, user.version());
        verify(cache)
                .put(argThat(u -> u.uniqueId().equals(uniqueId) && u.username().equals(username)));
    }

    @Test
    void loadUpdatesExistingUser() {
        UUID uniqueId = UUID.randomUUID();
        String oldName = "Old";
        String newName = "New";
        SimpleCotaniUser existing =
                SimpleCotaniUser.createNew(uniqueId, oldName, 1_000L).withVersion(5L);

        when(repository.find(uniqueId, newName)).thenReturn(CompletableFuture.completedFuture(Optional.of(existing)));

        SimpleCotaniUser user =
                service.load(uniqueId, newName).toCompletableFuture().join();

        assertEquals(newName, user.username());
        assertEquals(5L, user.version());
        assertTrue(user.lastJoinAt() > 0);
        verify(cache).put(user);
    }

    @Test
    void unloadUpdatesLastQuitAtAndRemoves() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1_000L);

        when(cache.findInternal(uniqueId)).thenReturn(Optional.of(user));
        when(cache.save(uniqueId)).thenReturn(CompletableFuture.completedFuture(null));
        when(cache.remove(uniqueId, user.sessionId())).thenReturn(true);

        service.unload(uniqueId).toCompletableFuture().join();

        verify(cache).put(argThat(u -> u.uniqueId().equals(uniqueId) && u.lastQuitAt() > 0));
        verify(cache).save(uniqueId);
        verify(cache).remove(uniqueId, user.sessionId());
    }

    @Test
    void unloadDoesNothingWhenUserNotCached() {
        UUID uniqueId = UUID.randomUUID();
        when(cache.findInternal(uniqueId)).thenReturn(Optional.empty());

        service.unload(uniqueId).toCompletableFuture().join();

        verify(cache, never()).save(any());
        verify(cache, never()).remove(any(), any());
    }

    @Test
    void saveDelegatesToCache() {
        UUID uniqueId = UUID.randomUUID();
        when(cache.contains(uniqueId)).thenReturn(true);
        when(cache.save(uniqueId)).thenReturn(CompletableFuture.completedFuture(null));

        service.save(uniqueId).toCompletableFuture().join();

        verify(cache).save(uniqueId);
    }

    @Test
    void saveAllDelegatesToCache() {
        when(cache.saveAll()).thenReturn(CompletableFuture.completedFuture(null));

        service.saveAll().toCompletableFuture().join();

        verify(cache).saveAll();
    }

    @Test
    void findAsyncReturnsUser() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        when(cache.find(uniqueId)).thenReturn(Optional.of(user));

        Optional<CotaniUser> result =
                service.findAsync(uniqueId).toCompletableFuture().join();

        assertTrue(result.isPresent());
        assertEquals(user.uniqueId(), result.get().uniqueId());
    }

    @Test
    void getOrThrowAsyncThrowsWhenAbsent() {
        UUID uniqueId = UUID.randomUUID();
        when(cache.find(uniqueId)).thenReturn(Optional.empty());
        when(repository.findByUniqueId(uniqueId)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        var exception = assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> service.getOrThrowAsync(uniqueId).toCompletableFuture().join());

        assertTrue(exception.getCause() instanceof UserNotLoadedException);
    }

    @Test
    void isLoadedAsyncReturnsCacheState() {
        UUID uniqueId = UUID.randomUUID();
        when(cache.contains(uniqueId)).thenReturn(true);

        assertTrue(service.isLoadedAsync(uniqueId).toCompletableFuture().join());
    }

    @Test
    void findAsyncFallsBackToRepositoryWhenCacheMiss() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        when(cache.find(uniqueId)).thenReturn(Optional.empty());
        when(repository.findByUniqueId(uniqueId)).thenReturn(CompletableFuture.completedFuture(Optional.of(user)));

        Optional<CotaniUser> result =
                service.findAsync(uniqueId).toCompletableFuture().join();

        assertTrue(result.isPresent());
        assertEquals(user.uniqueId(), result.get().uniqueId());
        verify(repository).findByUniqueId(uniqueId);
    }

    @Test
    void loadUsesCacheHit() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        when(cache.findInternal(uniqueId)).thenReturn(Optional.of(user));

        SimpleCotaniUser result =
                service.load(uniqueId, "NewName").toCompletableFuture().join();

        assertEquals(uniqueId, result.uniqueId());
        assertEquals("NewName", result.username());
        verify(cache).put(result);
        verifyNoInteractions(repository);
    }

    @Test
    void findAsyncUsesOngoingLoad() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);

        CompletableFuture<Optional<SimpleCotaniUser>> repositoryFuture = new CompletableFuture<>();
        when(repository.find(uniqueId, "Steve")).thenReturn(repositoryFuture);
        when(cache.findInternal(uniqueId)).thenReturn(Optional.empty());
        when(cache.find(uniqueId)).thenReturn(Optional.empty());

        // Trigger load but keep it unresolved
        service.load(uniqueId, "Steve");

        // Call findAsync - it should return a stage that completes when repositoryFuture completes
        CompletionStage<Optional<CotaniUser>> findFuture = service.findAsync(uniqueId);

        assertFalse(findFuture.toCompletableFuture().isDone());

        // Resolve repositoryFuture
        repositoryFuture.complete(Optional.of(user));

        Optional<CotaniUser> result = findFuture.toCompletableFuture().join();
        assertTrue(result.isPresent());
        assertEquals(user.uniqueId(), result.get().uniqueId());

        // Verify that findByUniqueId was NOT called since it used the ongoing load
        verify(repository, never()).findByUniqueId(uniqueId);
    }
}

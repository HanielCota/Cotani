package com.cotani.user.internal.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.user.api.CotaniUser;
import com.cotani.user.api.UserNotLoadedException;
import com.cotani.user.internal.cache.UserCache;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        when(cache.updateIfSession(eq(uniqueId), eq(user.sessionId()), any()))
                .thenAnswer(invocation -> Optional.of(invocation
                        .<UnaryOperator<SimpleCotaniUser>>getArgument(2)
                        .apply(user)));
        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(cache.remove(uniqueId, user.sessionId())).thenReturn(true);

        service.unload(uniqueId).toCompletableFuture().join();

        verify(repository).save(argThat(saved -> saved.lastQuitAt() > 0 && saved.version() == 1L));
        verify(cache).remove(uniqueId, user.sessionId());
    }

    @Test
    void unloadDoesNothingWhenUserNotCached() {
        UUID uniqueId = UUID.randomUUID();
        when(cache.findInternal(uniqueId)).thenReturn(Optional.empty());

        service.unload(uniqueId).toCompletableFuture().join();

        verify(repository, never()).save(any());
        verify(cache, never()).remove(any(), any());
    }

    @Test
    void saveIncrementsVersionAndCallsRepository() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1_000L).withVersion(2L);
        when(cache.findInternal(uniqueId)).thenReturn(Optional.of(user));
        when(cache.updateIfSession(eq(uniqueId), eq(user.sessionId()), any()))
                .thenAnswer(invocation -> Optional.of(invocation
                        .<UnaryOperator<SimpleCotaniUser>>getArgument(2)
                        .apply(user)));
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.save(uniqueId).toCompletableFuture().join();

        verify(repository).save(argThat(u -> u.version() == 3L));
    }

    @Test
    void saveAllSavesAllCachedUsers() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1_000L).withVersion(1L);
        when(cache.allInternal()).thenReturn(List.of(user));
        when(cache.updateIfSession(eq(uniqueId), eq(user.sessionId()), any()))
                .thenAnswer(invocation -> Optional.of(invocation
                        .<UnaryOperator<SimpleCotaniUser>>getArgument(2)
                        .apply(user)));
        when(repository.saveAll(any())).thenReturn(CompletableFuture.completedFuture(null));

        service.saveAll().toCompletableFuture().join();

        verify(repository)
                .saveAll(argThat(savedList ->
                        savedList.size() == 1 && savedList.iterator().next().version() == 2L));
    }

    @Test
    void oldSessionSaveCompletesBeforeNewSessionSave() {
        var uniqueId = UUID.randomUUID();
        var oldSession = SimpleCotaniUser.createNew(uniqueId, "Steve", 1_000L);
        var newSession = oldSession.withNewSessionId();
        when(cache.findInternal(uniqueId)).thenReturn(Optional.of(oldSession)).thenReturn(Optional.of(newSession));
        when(cache.updateIfSession(eq(uniqueId), any(UUID.class), any())).thenAnswer(invocation -> {
            var expectedSession = invocation.<UUID>getArgument(1);
            var current = expectedSession.equals(oldSession.sessionId()) ? oldSession : newSession;

            return Optional.of(
                    invocation.<UnaryOperator<SimpleCotaniUser>>getArgument(2).apply(current));
        });
        var oldSave = new CompletableFuture<Void>();
        var newSave = new CompletableFuture<Void>();
        when(repository.save(any())).thenReturn(oldSave).thenReturn(newSave);

        var unloading = service.unload(uniqueId).toCompletableFuture();
        var savingNewSession = service.save(uniqueId).toCompletableFuture();

        verify(repository, times(1)).save(any());
        assertFalse(savingNewSession.isDone());

        oldSave.complete(null);
        verify(repository, times(2)).save(any());
        newSave.complete(null);

        assertDoesNotThrow(unloading::join);
        assertDoesNotThrow(savingNewSession::join);
        var savedUsers = ArgumentCaptor.forClass(SimpleCotaniUser.class);
        verify(repository, times(2)).save(savedUsers.capture());
        assertEquals(
                oldSession.sessionId(), savedUsers.getAllValues().getFirst().sessionId());
        assertEquals(newSession.sessionId(), savedUsers.getAllValues().getLast().sessionId());
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
                CompletionException.class,
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

    @Test
    void findByNameAsyncUsesCacheHit() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UUID.randomUUID(), "Steve", 1L);
        when(cache.findByUsername("Steve")).thenReturn(Optional.of(user));

        Optional<CotaniUser> result =
                service.findByNameAsync("Steve").toCompletableFuture().join();

        assertTrue(result.isPresent());
        assertEquals("Steve", result.get().username());
        verifyNoInteractions(repository);
    }

    @Test
    void findByNameAsyncFallsBackToRepository() {
        SimpleCotaniUser user = SimpleCotaniUser.createNew(UUID.randomUUID(), "Alex", 1L);
        when(cache.findByUsername("Alex")).thenReturn(Optional.empty());
        when(repository.findByUsername("Alex")).thenReturn(CompletableFuture.completedFuture(Optional.of(user)));

        Optional<CotaniUser> result =
                service.findByNameAsync("Alex").toCompletableFuture().join();

        assertTrue(result.isPresent());
        assertEquals("Alex", result.get().username());
        verify(repository).findByUsername("Alex");
    }

    @Test
    void findCachedDelegatesToCache() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        when(cache.find(uniqueId)).thenReturn(Optional.of(user));

        Optional<CotaniUser> result = service.findCached(uniqueId);
        assertTrue(result.isPresent());
        assertEquals(uniqueId, result.get().uniqueId());
    }

    @Test
    void isLoadedDelegatesToCache() {
        UUID uniqueId = UUID.randomUUID();
        when(cache.contains(uniqueId)).thenReturn(true);

        assertTrue(service.isLoaded(uniqueId));
    }
}

package com.cotani.user.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cotani.user.internal.cache.UserCache;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("NullAway")
class SimpleUserServiceConcurrencyTest {
    private final UserCache cache = new UserCache();
    private final UserRepository repository = mock(UserRepository.class);
    private final SimpleUserService service = new SimpleUserService(cache, repository);

    @Test
    void concurrentLoadsForSameKeyShareSingleRepositoryCall() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        CompletableFuture<Optional<SimpleCotaniUser>> repositoryFuture = new CompletableFuture<>();
        when(repository.find(uniqueId, "Steve")).thenReturn(repositoryFuture);

        CompletableFuture<SimpleCotaniUser> first =
                service.load(uniqueId, "Steve").toCompletableFuture();
        CompletableFuture<SimpleCotaniUser> second =
                service.load(uniqueId, "Steve").toCompletableFuture();
        assertFalse(first.isDone());
        assertFalse(second.isDone());

        repositoryFuture.complete(Optional.empty());

        SimpleCotaniUser firstUser = first.get(5, TimeUnit.SECONDS);
        SimpleCotaniUser secondUser = second.get(5, TimeUnit.SECONDS);
        assertSame(firstUser, secondUser);
        verify(repository, org.mockito.Mockito.times(1)).find(uniqueId, "Steve");
        assertTrue(cache.contains(uniqueId));
    }

    @Test
    void loadFailureRemovesPendingEntryAndAllowsRetry() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        when(repository.find(uniqueId, "Steve"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        assertThrows(
                ExecutionException.class,
                () -> service.load(uniqueId, "Steve").toCompletableFuture().get(5, TimeUnit.SECONDS));

        assertFalse(cache.contains(uniqueId));

        when(repository.find(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        SimpleCotaniUser loaded =
                service.load(uniqueId, "Steve").toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(uniqueId, loaded.uniqueId());
        assertTrue(cache.contains(uniqueId));
    }

    @Test
    void clearCacheDuringLoadPreventsCachePopulation() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        CompletableFuture<Optional<SimpleCotaniUser>> repositoryFuture = new CompletableFuture<>();
        when(repository.find(uniqueId, "Steve")).thenReturn(repositoryFuture);

        CompletableFuture<SimpleCotaniUser> loading =
                service.load(uniqueId, "Steve").toCompletableFuture();
        service.clearCache();
        repositoryFuture.complete(Optional.empty());

        loading.get(5, TimeUnit.SECONDS);
        assertFalse(cache.contains(uniqueId));
    }

    @Test
    void unloadPersistsQuitAndRemovesFromCache() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        when(repository.find(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        service.load(uniqueId, "Steve").toCompletableFuture().get(5, TimeUnit.SECONDS);

        service.unload(uniqueId).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertFalse(cache.contains(uniqueId));
        ArgumentCaptor<SimpleCotaniUser> saved = ArgumentCaptor.forClass(SimpleCotaniUser.class);
        verify(repository).save(saved.capture());
        assertTrue(saved.getValue().lastQuitAt() > 0);
        assertEquals(1L, saved.getValue().version());
    }

    @Test
    void saveIncrementsVersionInCache() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        when(repository.find(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(repository.save(any())).thenReturn(CompletableFuture.completedFuture(null));
        service.load(uniqueId, "Steve").toCompletableFuture().get(5, TimeUnit.SECONDS);

        service.save(uniqueId).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(1L, cache.findInternal(uniqueId).orElseThrow().version());
    }

    @Test
    void unloadSkipsPersistenceWhenSessionChanged() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        when(repository.find(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        service.load(uniqueId, "Steve").toCompletableFuture().get(5, TimeUnit.SECONDS);
        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        cache.put(current.withNewSessionId());

        service.unload(uniqueId).toCompletableFuture().get(5, TimeUnit.SECONDS);

        verify(repository, never()).save(any());
        assertTrue(cache.contains(uniqueId));
    }

    @Test
    void saveSkipsPersistenceWhenSessionChanged() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        when(repository.find(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        service.load(uniqueId, "Steve").toCompletableFuture().get(5, TimeUnit.SECONDS);
        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        cache.put(current.withNewSessionId());

        service.save(uniqueId).toCompletableFuture().get(5, TimeUnit.SECONDS);

        verify(repository, never()).save(any());
    }

    @Test
    void saveAllWithEmptyCacheCompletesImmediately() throws Exception {
        service.saveAll().toCompletableFuture().get(5, TimeUnit.SECONDS);

        verifyNoInteractions(repository);
    }

    @Test
    void unloadWithEmptyCacheCompletesImmediately() throws Exception {
        service.unload(UUID.randomUUID()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        verifyNoInteractions(repository);
    }

    @Test
    void saveWithEmptyCacheCompletesImmediately() throws Exception {
        service.save(UUID.randomUUID()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        verifyNoInteractions(repository);
    }

    @Test
    void publicApiRejectsNullUniqueId() {
        assertThrows(NullPointerException.class, () -> service.findAsync(null));
        assertThrows(NullPointerException.class, () -> service.getOrThrowAsync(null));
        assertThrows(NullPointerException.class, () -> service.isLoadedAsync(null));
        assertThrows(NullPointerException.class, () -> service.load(null, "Steve"));
        assertThrows(NullPointerException.class, () -> service.unload(null));
        assertThrows(NullPointerException.class, () -> service.save(null));
    }

    @Test
    void loadRejectsNullUsername() {
        assertThrows(NullPointerException.class, () -> service.load(UUID.randomUUID(), null));
    }
}

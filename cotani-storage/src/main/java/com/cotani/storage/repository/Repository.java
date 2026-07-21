package com.cotani.storage.repository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Contract for a storage repository.
 *
 * <p>All methods are asynchronous: every operation returns a {@link java.util.concurrent.CompletionStage}
 * and performs I/O off the main thread. Do not block on the returned stage from the server thread;
 * compose through {@code thenApply}/{@code thenCompose} or use {@code CotaniStorage.closeAsync()} for shutdown.
 */
public interface Repository<K, T> {

    CompletionStage<Optional<T>> findById(K id);

    CompletionStage<Boolean> exists(K id);

    CompletionStage<Void> save(T entity);

    CompletionStage<Void> deleteById(K id);
}

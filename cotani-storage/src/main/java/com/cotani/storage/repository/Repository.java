package com.cotani.storage.repository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface Repository<K, T> {

    CompletionStage<Optional<T>> findById(K id);

    CompletionStage<Boolean> exists(K id);

    CompletionStage<Void> save(T entity);

    CompletionStage<Void> deleteById(K id);
}

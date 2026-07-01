package br.com.cotani.storage.repository;

import br.com.cotani.storage.future.OptionalStorageFuture;
import br.com.cotani.storage.future.StorageFuture;

public interface Repository<K, T> {

    OptionalStorageFuture<T> findById(K id);

    StorageFuture<Boolean> exists(K id);

    StorageFuture<Void> save(T entity);

    StorageFuture<Void> deleteById(K id);
}

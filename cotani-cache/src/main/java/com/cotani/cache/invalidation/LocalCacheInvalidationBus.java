package com.cotani.cache.invalidation;

import com.cotani.api.InternalApi;
import com.cotani.task.util.CompletionStages;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** In-process invalidation bus, useful when multiple cache instances share one JVM. */
@InternalApi
public final class LocalCacheInvalidationBus<K> implements CacheInvalidationBus<K> {

    private final CopyOnWriteArrayList<Consumer<CacheInvalidation<K>>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public CacheInvalidationSubscription subscribe(Consumer<CacheInvalidation<K>> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public CompletionStage<Void> publish(CacheInvalidation<K> invalidation) {
        Objects.requireNonNull(invalidation, "invalidation");
        for (var listener : listeners) {
            listener.accept(invalidation);
        }
        return CompletionStages.completedVoid();
    }
}

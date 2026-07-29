package com.cotani.cache.invalidation;

import com.cotani.task.util.CompletionStages;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Local-only invalidation policy used when no distributed transport is configured. */
public final class NoopCacheInvalidationBus<K> implements CacheInvalidationBus<K> {

    @Override
    public CacheInvalidationSubscription subscribe(Consumer<CacheInvalidation<K>> listener) {
        Objects.requireNonNull(listener, "listener");
        return CacheInvalidationSubscription.noop();
    }

    @Override
    public CompletionStage<Void> publish(CacheInvalidation<K> invalidation) {
        Objects.requireNonNull(invalidation, "invalidation");
        return CompletionStages.completedVoid();
    }
}

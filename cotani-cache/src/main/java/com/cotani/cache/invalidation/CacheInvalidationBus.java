package com.cotani.cache.invalidation;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Cooperative invalidation SPI for caches that share a repository.
 *
 * <p>Every process that writes the repository must publish through the same bus. Implementations
 * may bridge Redis, a message broker, or another deployment-specific transport.
 */
public interface CacheInvalidationBus<K> {
    CacheInvalidationSubscription subscribe(Consumer<CacheInvalidation<K>> listener);

    CompletionStage<Void> publish(CacheInvalidation<K> invalidation);
}

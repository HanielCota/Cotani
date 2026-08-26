package com.cotani.metrics.binder;

import com.cotani.cache.stats.CacheStatsView;
import com.cotani.metrics.api.MeterBinder;
import com.cotani.metrics.api.MetricsRegistry;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class CacheMetricsBinder implements MeterBinder {
    private final String cacheName;
    private final Supplier<CacheStatsView> statsSupplier;

    public CacheMetricsBinder(String cacheName, Supplier<CacheStatsView> statsSupplier) {
        this.cacheName = Objects.requireNonNull(cacheName, "cacheName");
        this.statsSupplier = Objects.requireNonNull(statsSupplier, "statsSupplier");
    }

    @Override
    public void bindTo(MetricsRegistry registry) {
        Objects.requireNonNull(registry, "registry");

        var memoized = new MemoizingStatsSupplier(statsSupplier);

        registry.gauge("cache.size", () -> memoized.get().size(), "cache", cacheName);
        registry.gauge("cache.hits", () -> memoized.get().hitCount(), "cache", cacheName);
        registry.gauge("cache.misses", () -> memoized.get().missCount(), "cache", cacheName);
        registry.gauge("cache.hit_rate", () -> memoized.get().hitRate(), "cache", cacheName);
        registry.gauge("cache.evictions", () -> memoized.get().evictionCount(), "cache", cacheName);
        registry.gauge("cache.dirty", () -> memoized.get().dirtyEntries(), "cache", cacheName);
    }

    private static final class MemoizingStatsSupplier implements Supplier<CacheStatsView> {
        private final Supplier<CacheStatsView> delegate;
        private volatile @Nullable CacheStatsView cached;
        private volatile long cachedAt;

        MemoizingStatsSupplier(Supplier<CacheStatsView> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CacheStatsView get() {
            var now = System.currentTimeMillis();
            var snapshot = cached;

            if (snapshot == null || now - cachedAt > 5000) {
                snapshot = delegate.get();
                cached = snapshot;
                cachedAt = now;
            }
            return snapshot;
        }
    }
}

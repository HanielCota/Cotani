package net.cotani.metrics.binder;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import java.util.function.Supplier;
import net.cotani.metrics.api.MeterBinder;
import net.cotani.metrics.api.MetricsRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Binds {@code cotani-storage} database pool statistics to a {@link MetricsRegistry}.
 */
public final class StorageMetricsBinder implements MeterBinder {
    /**
     * Immutable snapshot of storage connection pool statistics.
     *
     * @param activeConnections number of active connections currently in use
     * @param idleConnections   number of idle connections in the pool
     * @param totalConnections  total connections managed by the pool
     * @param threadsAwaiting   number of threads waiting for a connection
     */
    public record StoragePoolStatsView(
            int activeConnections, int idleConnections, int totalConnections, int threadsAwaiting) {}

    private final String poolName;
    private final Supplier<StoragePoolStatsView> statsSupplier;

    public StorageMetricsBinder(String poolName, Supplier<StoragePoolStatsView> statsSupplier) {
        this.poolName = Objects.requireNonNull(poolName, "poolName");
        this.statsSupplier = Objects.requireNonNull(statsSupplier, "statsSupplier");
    }

    public static StorageMetricsBinder forHikari(HikariDataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");

        String name = dataSource.getPoolName() != null ? dataSource.getPoolName() : "default";

        return forHikari(name, dataSource);
    }

    public static StorageMetricsBinder forHikari(String poolName, HikariDataSource dataSource) {
        Objects.requireNonNull(poolName, "poolName");
        Objects.requireNonNull(dataSource, "dataSource");

        return new StorageMetricsBinder(poolName, () -> {
            var mxBean = dataSource.getHikariPoolMXBean();

            if (mxBean == null) {
                return new StoragePoolStatsView(0, 0, 0, 0);
            }

            return new StoragePoolStatsView(
                    mxBean.getActiveConnections(),
                    mxBean.getIdleConnections(),
                    mxBean.getTotalConnections(),
                    mxBean.getThreadsAwaitingConnection());
        });
    }

    @Override
    public void bindTo(MetricsRegistry registry) {
        Objects.requireNonNull(registry, "registry");

        var memoized = new MemoizingStorageSupplier(statsSupplier);

        registry.gauge("storage.pool.active", () -> memoized.get().activeConnections(), "pool", poolName);
        registry.gauge("storage.pool.idle", () -> memoized.get().idleConnections(), "pool", poolName);
        registry.gauge("storage.pool.total", () -> memoized.get().totalConnections(), "pool", poolName);
        registry.gauge("storage.pool.awaiting", () -> memoized.get().threadsAwaiting(), "pool", poolName);
    }

    private static final class MemoizingStorageSupplier implements Supplier<StoragePoolStatsView> {
        private final Supplier<StoragePoolStatsView> delegate;
        private volatile @Nullable StoragePoolStatsView cached;
        private volatile long cachedAt;

        MemoizingStorageSupplier(Supplier<StoragePoolStatsView> delegate) {
            this.delegate = delegate;
        }

        @Override
        public StoragePoolStatsView get() {
            var now = System.currentTimeMillis();
            var snapshot = cached;

            if (snapshot == null || now - cachedAt > 2000) {
                snapshot = delegate.get();
                cached = snapshot;
                cachedAt = now;
            }
            return snapshot;
        }
    }
}

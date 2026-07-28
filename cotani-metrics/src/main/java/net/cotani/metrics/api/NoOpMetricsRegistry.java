package net.cotani.metrics.api;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Zero-allocation, zero-overhead no-op implementation of {@link MetricsRegistry}.
 * Used when metrics collection is disabled.
 */
public final class NoOpMetricsRegistry implements MetricsRegistry {

    /**
     * Singleton instance of the no-op registry.
     */
    public static final NoOpMetricsRegistry INSTANCE = new NoOpMetricsRegistry();

    public NoOpMetricsRegistry() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void counter(String name, String... tags) {
        // No-op
    }

    @Override
    public void counter(String name, double amount, String... tags) {
        // No-op
    }

    @Override
    public void gauge(String name, Supplier<Number> numberSupplier, String... tags) {
        // No-op
    }

    @Override
    public void timer(String name, Duration duration, String... tags) {
        // No-op
    }

    @Override
    public void register(MeterBinder binder) {
        Objects.requireNonNull(binder, "binder");
        // No-op
    }

    @Override
    public Optional<MeterRegistry> meterRegistry() {
        return Optional.empty();
    }

    @Override
    public void close() {
        // No-op
    }
}

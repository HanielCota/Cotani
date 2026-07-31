package net.cotani.metrics.api;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Primary API contract for Cotani metrics collection.
 */
public interface MetricsRegistry extends AutoCloseable {
    /**
     * Indicates whether metric collection is active.
     *
     * @return true if metrics enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Increments a counter by 1.
     *
     * @param name metric name
     * @param tags optional key-value tag pairs
     */
    void counter(String name, String... tags);

    /**
     * Increments a counter by a given amount.
     *
     * @param name   metric name
     * @param amount amount to increment
     * @param tags   optional key-value tag pairs
     */
    void counter(String name, double amount, String... tags);

    /**
     * Registers a gauge backed by a number supplier.
     *
     * @param name           metric name
     * @param numberSupplier value supplier
     * @param tags           optional key-value tag pairs
     */
    void gauge(String name, Supplier<Number> numberSupplier, String... tags);

    /**
     * Records execution time in a timer metric.
     *
     * @param name     metric name
     * @param duration recorded duration
     * @param tags     optional key-value tag pairs
     */
    void timer(String name, Duration duration, String... tags);

    /**
     * Binds a meter binder to this registry.
     *
     * @param binder meter binder instance
     */
    void register(MeterBinder binder);

    /**
     * Returns the underlying Micrometer {@link MeterRegistry} if present.
     *
     * @return optional micrometer meter registry
     */
    Optional<MeterRegistry> meterRegistry();

    @Override
    void close();
}

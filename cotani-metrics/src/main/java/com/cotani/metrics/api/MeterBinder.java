package com.cotani.metrics.api;

/**
 * Interface for binding a set of metrics to a {@link MetricsRegistry}.
 */
@FunctionalInterface
public interface MeterBinder {
    /**
     * Binds metrics to the target metrics registry.
     *
     * @param registry the target metrics registry
     */
    void bindTo(MetricsRegistry registry);
}

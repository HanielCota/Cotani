package com.cotani.metrics;

import java.util.Objects;
import net.cotani.metrics.CotaniMetricsModule;
import net.cotani.metrics.config.MetricsConfig;

/** Stable {@code com.cotani} entry point for the legacy 1.x metrics namespace. */
public final class CotaniMetrics {
    private CotaniMetrics() {}

    public static CotaniMetricsModule create(MetricsConfig config) {
        return CotaniMetricsModule.create(Objects.requireNonNull(config, "config"));
    }
}

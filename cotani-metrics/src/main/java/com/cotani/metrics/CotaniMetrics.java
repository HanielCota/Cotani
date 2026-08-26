package com.cotani.metrics;

import com.cotani.metrics.config.MetricsConfig;
import java.util.Objects;

/** Stable {@code com.cotani} entry point for the legacy 1.x metrics namespace. */
public final class CotaniMetrics {
    private CotaniMetrics() {}

    public static CotaniMetricsModule create(MetricsConfig config) {
        return CotaniMetricsModule.create(Objects.requireNonNull(config, "config"));
    }
}

package net.cotani.metrics;

import com.cotani.api.InternalApi;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.cotani.metrics.api.MeterBinder;
import net.cotani.metrics.api.MetricsRegistry;

/**
 * Micrometer-backed implementation of {@link MetricsRegistry}.
 */
@InternalApi
public final class CotaniMetricsRegistry implements MetricsRegistry {

    private final MeterRegistry meterRegistry;
    private final String prefix;

    public CotaniMetricsRegistry(String prefix) {
        this(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), prefix);
    }

    public CotaniMetricsRegistry(MeterRegistry meterRegistry, String prefix) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.prefix = prefix.trim();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void counter(String name, String... tags) {
        counter(name, 1.0, tags);
    }

    @Override
    public void counter(String name, double amount, String... tags) {
        Objects.requireNonNull(name, "name");
        meterRegistry.counter(formatName(name), toTags(tags)).increment(amount);
    }

    @Override
    public void gauge(String name, Supplier<Number> numberSupplier, String... tags) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(numberSupplier, "numberSupplier");
        Gauge.builder(formatName(name), numberSupplier, s -> {
                    Number val = s.get();
                    return val.doubleValue();
                })
                .tags(toTags(tags))
                .register(meterRegistry);
    }

    @Override
    public void timer(String name, Duration duration, String... tags) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(duration, "duration");
        meterRegistry.timer(formatName(name), toTags(tags)).record(duration);
    }

    @Override
    public void register(MeterBinder binder) {
        Objects.requireNonNull(binder, "binder");
        binder.bindTo(this);
    }

    @Override
    public Optional<MeterRegistry> meterRegistry() {
        return Optional.of(meterRegistry);
    }

    public Optional<PrometheusMeterRegistry> prometheusRegistry() {
        if (meterRegistry instanceof PrometheusMeterRegistry prometheusMeterRegistry) {
            return Optional.of(prometheusMeterRegistry);
        }
        return Optional.empty();
    }

    private String formatName(String name) {
        if (prefix.isEmpty() || name.startsWith(prefix + ".")) {
            return name;
        }
        return prefix + "." + name;
    }

    private Tags toTags(String[] tags) {
        if (tags.length == 0) {
            return Tags.empty();
        }
        if (tags.length % 2 != 0) {
            String[] padded = new String[tags.length + 1];
            System.arraycopy(tags, 0, padded, 0, tags.length);
            padded[tags.length] = "";
            return Tags.of(padded);
        }
        return Tags.of(tags);
    }

    @Override
    public void close() {
        meterRegistry.close();
    }
}

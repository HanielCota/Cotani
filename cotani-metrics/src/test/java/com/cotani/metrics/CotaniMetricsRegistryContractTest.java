package com.cotani.metrics;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.metrics.api.MetricsRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link CotaniMetricsRegistry} satisfies the {@link MetricsRegistry} contract.
 */
class CotaniMetricsRegistryContractTest extends com.cotani.metrics.api.MetricsRegistryContractTest {

    @Override
    protected MetricsRegistry newRegistry() {
        return new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");
    }

    @Override
    protected boolean expectEnabled() {
        return true;
    }

    @Override
    protected boolean expectMeterRegistryPresent() {
        return true;
    }

    @Test
    void shouldInvokeBinderExactlyOnceWithTargetRegistryOnRegister() {
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");
        AtomicInteger invocations = new AtomicInteger();

        registry.register(r -> {
            invocations.incrementAndGet();
            assertSame(registry, r);
        });

        assertEquals(1, invocations.get());
    }
}

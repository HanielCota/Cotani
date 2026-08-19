package net.cotani.metrics.api;

/**
 * Verifies that {@link NoOpMetricsRegistry} satisfies the {@link MetricsRegistry} contract.
 */
class NoOpMetricsRegistryContractTest extends MetricsRegistryContractTest {

    @Override
    protected MetricsRegistry newRegistry() {
        return new NoOpMetricsRegistry();
    }

    @Override
    protected boolean expectEnabled() {
        return false;
    }

    @Override
    protected boolean expectMeterRegistryPresent() {
        return false;
    }
}

# cotani-metrics

Micrometer-backed metrics for Cotani modules with an optional Prometheus HTTP endpoint.

## Usage

Metrics are disabled by default. Bind `MetricsConfig` from `cotani-config`, create one module during startup and register it with the lifecycle:

```java
MetricsConfig config = configs.file("config.yml")
    .bindOrThrow("metrics", MetricsConfig.class);

var metrics = CotaniMetrics.create(config);
Cotani lifecycle = Cotani.forPlugin(plugin)
    .with(metrics)
    .build();

metrics.registry().counter("economy.transactions", "result", "success");
```

When Prometheus export is enabled, bind to a private interface such as `127.0.0.1` unless the endpoint is protected by the surrounding network. Closing the module stops the HTTP server and closes the registry.

Use the `com.cotani.metrics.CotaniMetrics` factory for new code. Returned 1.x types retain their `net.cotani.metrics` package for binary compatibility; see the [migration notes](../docs/migration-1.x.md).

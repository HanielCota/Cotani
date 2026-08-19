# cotani-metrics

## Scope

Micrometer-backed metrics collection, gauge/counter/timer registrations and Prometheus HTTP scrape endpoint.

## Hard rules

1. Metrics collection must be zero-overhead and non-blocking when disabled (`NoOpMetricsRegistry`).
2. Gauges must never perform slow I/O or acquire heavy locks during evaluation; use memoized snapshots.
3. Never block the Paper main thread or region threads during metric recording or HTTP scraping.
4. Embedded Prometheus server must use dedicated async/virtual threads and bind to private interfaces by default.
5. Keep tag cardinality bounded; never use unbounded inputs (e.g. raw player messages or arbitrary UUIDs) as metric tags.
6. Use the `com.cotani.metrics.CotaniMetrics` factory for instantiation.

## Anti-patterns

- Invoking blocking operations inside a `gauge` supplier.
- Creating high-cardinality metric names or tags dynamically.
- Performing raw I/O or server state mutations inside `MeterBinder` bindings.

## Related skills

- `java-engineering-standards`
- `java-api-standards`

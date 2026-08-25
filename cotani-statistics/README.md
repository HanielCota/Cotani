# cotani-statistics

Asynchronous, Bukkit-free player statistics with atomic increments, deterministic bounded rankings, domain events, and a SQL adapter.

## What it provides

- immutable `StatisticId`, `StatisticEntry`, and ranking values;
- atomic positive increments with overflow protection;
- stable idempotency keys for safe retries after timeouts;
- per-player mutation ordering without blocking callers;
- bounded rankings ordered by value and UUID;
- best-effort `StatisticChangedEvent` publication;
- in-memory and SQLite/MySQL/MariaDB storage through `CotaniStorage`.

## Registration

Register the migrations before starting storage:

```java
CotaniStorage storage = CotaniStorage.create(plugin)
        .migrations(CotaniStatistics.migrations().toArray(Migration[]::new))
        .build();

storage.startAsync().thenCompose(started -> {
    StatisticService statistics = CotaniStatistics.storage(started, eventBus);
    return statistics.incrementAsync(playerId, StatisticId.of("blocks-mined"), 1)
            .thenCompose(ignored -> statistics.closeAsync())
            .thenCompose(ignored -> started.closeAsync());
});
```

`StatisticService` never stores live Bukkit objects. Capture UUIDs on the owning thread, perform statistics I/O through `CompletionStage`, and transition back to the player/entity thread only when a caller needs to update Paper state.

For operations that may be retried, create a `StatisticOperationId` once and reuse it with the four-argument `incrementAsync` overload. A replay returns the original result without applying the value a second time. Use this overload only for retryable operations: its SQL ledger is intentionally not written for ordinary increments, avoiding unbounded growth for high-frequency counters. Repository implementations must complete mutations within their own storage timeout; the service also applies its configured repository timeout and observes late completions so durable changes still produce events.

The SQL adapter owns no storage lifecycle; close the service and storage asynchronously as part of the plugin shutdown flow.

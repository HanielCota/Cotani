# Asynchronous API contracts

Cotani async methods return `CompletionStage<T>` or `TaskChain<T>` and never require callers to block for a result.

## Thread boundaries

- Inputs are validated on the calling thread before work is scheduled.
- Storage, file and other blocking I/O executes on the explicit executor owned by the module.
- A completion stage may complete on that worker executor. It does not imply a Paper main, region or entity thread.
- Use `TaskChain.consumeGlobal`, `consumeRegion` or `consumeEntity` before accessing Bukkit/Paper state.
- Capture `UUID`, primitive values, records and immutable collection snapshots before leaving a server-owned thread. Do not retain live `Player`, `World`, `Entity`, `Inventory` or `Block` references in async callbacks.

## Failure, timeout and cancellation

- Validation failures documented as synchronous may be thrown before a stage is returned.
- I/O and domain failures complete the returned stage exceptionally. Completion wrappers such as `CompletionException` may contain the domain cause.
- `TaskChain.timeout` completes the chain exceptionally; it cannot guarantee interruption of an external operation that ignores cancellation.
- Cancellation is best effort and propagates to Cotani-owned scheduled work. A remote database or Paper future may already have committed its side effect.
- Retry is accepted only for repeatable chains and must be limited to idempotent operations.

## Lifecycle

- After `closeAsync()` begins, a module rejects new work unless its API explicitly documents a different behavior.
- Concurrent close calls coalesce where the API exposes `closeAsync()`.
- Observe the returned close stage and log failures. Never call blocking `close()`, `join()` or `get()` on a Paper-owned thread.

The compile-checked examples in `docs-examples` exercise these boundaries during every build.

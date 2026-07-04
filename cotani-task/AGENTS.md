# cotani-task

## Scope

Scheduling, async execution and task chaining for Paper/Folia.

## Hard rules

1. Never call `future.join()`, `future.get()`, `Thread.sleep(...)` or `TimeUnit.*.sleep(...)` in application code.
2. Never use `CompletableFuture.supplyAsync(...)` or `CompletableFuture.runAsync(...)` without an explicit executor.
3. Do not capture live `Player`, `World`, `Entity`, `Inventory` or `Block` objects into async flows; capture immutable IDs instead.
4. Always return to the main thread through `TaskChain` or `PaperTaskScheduler` before touching Bukkit/Paper APIs.

## Patterns

### Scheduler creation

```java
PaperTaskScheduler scheduler = SchedulerFactory.create(plugin);
```

### Async chain returning to main thread

```java
scheduler.supplyAsync(() -> loadData(uuid))
    .thenGlobal(data -> updatePlayer(player, data))
    .toCompletionStage();
```

### Retry with timeout

```java
scheduler.supplyAsync("load", () -> fetchRemote())
    .timeout(Duration.ofSeconds(5))
    .retry(RetryPolicy.exponentialWithJitter(3, Duration.ofMillis(200)))
    .toCompletionStage();
```

## Anti-patterns

- Manual `Bukkit.getScheduler()` calls for task switching.
- `TaskChain.thenAsync(...)` followed by direct Bukkit API access inside the lambda.
- Retrying non-idempotent operations (e.g., money withdrawal without idempotency key).

## Related skills

- `java-async-standards`
- `paper-plugin-architecture`

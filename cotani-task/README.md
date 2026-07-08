# cotani-task

Scheduler and async execution framework for Paper and Folia. Manages global, region, entity, and async tasks safely, and provides `TaskChain` for chaining execution between threads.

## Overview

`cotani-task` introduces a thread-safe scheduler abstraction that integrates with PaperSpigot's region-aware scheduling model (including Folia compatibility). It offers fluent async task chains that handle thread-switching between asynchronous execution pools and the server main/region threads safely.

## Features

- **Paper & Folia Friendly**: Transparently delegates tasks to correct region/entity schedules or the global main thread.
- **Fluent Chaining (`TaskChain`)**: Chain async computations back into Paper main thread context with minimal boilerplate.
- **Fault-Tolerant Utilities**: Build retry mechanisms and timeouts directly into your asynchronous task pipelines.
- **Explicit Executors**: Enforces the use of explicit, configured schedulers rather than implicit/default threads.

## Usage

### 1. Scheduler Creation

Create the scheduler instance in your plugin's bootstrap:

```java
PaperTaskScheduler scheduler = SchedulerFactory.create(plugin);
```

### 2. Async Execution and Chaining (Thread Switching)

Execute a database or network operation asynchronously, then apply the results on the global server thread safely:

```java
scheduler.supplyAsync(() -> fetchUserData(uuid))
    .thenGlobal(data -> {
        // Safe to touch Bukkit/Paper API (e.g. Player, World, Inventory)
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(Component.text("Data loaded successfully!"));
            applyDataToPlayer(player, data);
        }
        return true;
    })
    .toCompletionStage();
```

### 3. Asynchronous Retries with Timeout

Run an idempotent operation with a timeout constraint and exponential backoff retry policy:

```java
scheduler.supplyAsync("fetch-stats", () -> fetchRemoteStats(uuid))
    .timeout(Duration.ofSeconds(5))
    .retry(RetryPolicy.exponentialWithJitter(3, Duration.ofMillis(200)))
    .toCompletionStage()
    .whenComplete((stats, error) -> {
        if (error != null) {
            plugin.getLogger().warning("Failed to fetch statistics: " + error.getMessage());
        }
    });
```

## Hard Rules & Best Practices

1. **No Blocking in App Code**: Never invoke blocking methods in the application threads. Avoid:
   - `future.join()`
   - `future.get()`
   - `Thread.sleep(...)` / `TimeUnit.SECONDS.sleep(...)`
   - `FutureUtils.await(...)` or similar custom sleep/blocking wrappers.
2. **Explicit Thread pools**: Do not use implicit executors like `CompletableFuture.supplyAsync(() -> ...)` or `CompletableFuture.runAsync(() -> ...)`. Always supply a scheduler or task instance.
3. **No Live Bukkit Objects in Async Flows**: Never capture mutable Bukkit/Paper instances (`Player`, `World`, `Entity`, `Inventory`, `Block`) into async lambda scopes. Capture immutable IDs (like `UUID` or location data records) instead, then resolve them once you return to the main thread.
4. **Boundary Switching**: Always return to the main server thread through `TaskChain` or `PaperTaskScheduler` before accessing Bukkit/Paper APIs.

## Anti-Patterns

- Using manual `Bukkit.getScheduler()` calls for task-switching logic.
- Directly calling Bukkit/Paper APIs inside an async lambda (e.g. after a `.thenAsync(...)` invocation).
- Applying retry policies on non-idempotent operations (such as credit/debit transactions) without verifying idempotency locks.

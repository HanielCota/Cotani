# cotani-cooldown

Thread-safe, non-blocking cooldown manager. Tracks actions, remaining duration, and persistent cooldown states.

## Overview

`cotani-cooldown` is a flexible, highly optimized cooldown manager supporting both transient in-memory tracking and persistent database-backed storage. It helps plugins prevent action spamming (commands, combat abilities, event triggers) by verifying cooldown limits asynchronously.

## Features

- **Granular Scoping**: Map cooldown targets globally, per user (`UUID`), or to specific in-game resources.
- **SQL Persistence**: Persistent cooldown states across server restarts backed by SQLite, MySQL, or MariaDB.
- **Distributed Atomicity**: Conditional SQL upsert permits only one acquisition across server/process instances sharing the same database.
- **Automatic Cleanup**: Expired records are automatically pruned to prevent table bloat.
- **Paper Events Integration**: Dispatches `CotaniCooldownStartEvent` and `CotaniCooldownDenyEvent` to hook into cooldown triggers.
- **Fluent API**: Builder-style API for quick verification checks.

## Usage

### 1. In-Memory Cooldown Verification

Check and start a daily cooldown for a user in-memory:

```java
CooldownService cooldowns = DefaultCooldownService.inMemory();

CooldownResult result = cooldowns.user(userId)
    .action("daily.reward")
    .duration(Duration.ofHours(24))
    .checkAndStart();

if (result.denied()) {
    Duration remaining = result.remaining();
    player.sendMessage("Cooldown active! Please wait " + remaining.toHours() + "h.");
} else {
    // Perform rewarded action
}
```

### 2. Distributed Cooldown with SQLite/MySQL/MariaDB

Register the module migrations while building storage, then create the shared asynchronous service:

```java
var storage = CotaniStorage.create(plugin)
    .backend(backend)
    .scheduler(scheduler)
    .migrations(CotaniCooldowns.migrations().toArray(Migration[]::new))
    .build();

storage.startAsync().thenAccept(started -> {
    DistributedCooldownService cooldowns = CotaniCooldowns.distributed(started, scheduler);
    cooldowns.checkAndStartAsync(key, Duration.ofMinutes(5))
        .thenAccept(result -> {
            // Handle allowed/denied without blocking.
        });
});
```

## Hard Rules & Best Practices

1. **Keep Database Cooldown Checks Off-Thread**: Database transactions must be resolved asynchronously. Never block the server's main thread to check persistence tables.
2. **Handle Event Results**: Use the event dispatchers (`CotaniCooldownStartEvent` / `CotaniCooldownDenyEvent`) to communicate with other plugin systems.
3. **Target Selection**: Select the narrowest target scope needed (`CooldownTargets.user(...)` for players, `CooldownTargets.global(...)` for global limits, and `CooldownTargets.resource(...)` for specific entities or items).
4. **Choose the Consistency Contract**: Use `CotaniCooldowns.inMemory()`/`cacheBacked()` for local limits and `CotaniCooldowns.distributed(...)` when multiple servers must share one atomic limit.
5. **Lifecycle**: Close the distributed service to cancel its cleanup timer; storage remains owned by the plugin lifecycle.

## Anti-Patterns

- Running database select queries on cooldown stores synchronously inside main-thread listeners.
- Re-triggering a cooldown multiple times within the same execution path.

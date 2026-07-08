# cotani-cooldown

Thread-safe, non-blocking cooldown manager. Tracks actions, remaining duration, and persistent cooldown states.

## Overview

`cotani-cooldown` is a flexible, highly optimized cooldown manager supporting both transient in-memory tracking and persistent database-backed storage. It helps plugins prevent action spamming (commands, combat abilities, event triggers) by verifying cooldown limits asynchronously.

## Features

- **Granular Scoping**: Map cooldown targets globally, per user (`UUID`), or to specific in-game resources.
- **SQL Persistence**: Persistent cooldown states across server restarts backed by SQLite/MySQL databases.
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

### 2. Persistent Cooldown with SQLite/MySQL

Initialize the service with SQL persistence and caching policies:

```java
CooldownStore store = new CacheCooldownStore(
    new SqlCooldownRepository(storage),
    scheduler
);

CooldownService cooldownService = new DefaultCooldownService(store);
```

## Hard Rules & Best Practices

1. **Keep Database Cooldown Checks Off-Thread**: Database transactions must be resolved asynchronously. Never block the server's main thread to check persistence tables.
2. **Handle Event Results**: Use the event dispatchers (`CotaniCooldownStartEvent` / `CotaniCooldownDenyEvent`) to communicate with other plugin systems.
3. **Target Selection**: Select the narrowest target scope needed (`CooldownTargets.user(...)` for players, `CooldownTargets.global(...)` for global limits, and `CooldownTargets.resource(...)` for specific entities or items).

## Anti-Patterns

- Running database select queries on cooldown stores synchronously inside main-thread listeners.
- Re-triggering a cooldown multiple times within the same execution path.

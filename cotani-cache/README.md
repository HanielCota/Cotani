# cotani-cache

Caffeine-backed asynchronous caching. Provides generic `DataCache` and player-focused `PlayerDataCache` featuring automatic dirty tracking, presets, and background saves.

## Overview

`cotani-cache` provides highly scalable in-memory caching solutions designed for Paper plugin development. It supports automatic background persistence (flushing changes back to your SQL/NoSQL storage) by tracking "dirty" entries, eliminating manual transaction logic on every small mutation.

## Features

- **Asynchronous Operations**: Reads, writes, and database flushing are offloaded from the main server thread.
- **Player Data Cache**: Special cache tailored for user lifecycle events (load on join, unload and save on quit).
- **Auto-Flush and Autosave**: Tracks state changes automatically and schedules background flush tasks.
- **Caffeine Engine**: Backed by the high-performance Caffeine library.
- **Dirty State Tracking**: Updates occur in-memory, dirty flags mark records, and background workers batch write modifications.
- **Bounded Save Fan-Out**: Bulk flush and shutdown limit concurrent repository saves (16 by default).
- **Optional Invalidation Bus**: Cache instances can coordinate clean-entry invalidation through a local or distributed bus implementation.

## Usage

### 1. Building a Player Data Cache

Build a player-bound cache mapping `UUID` to custom domain objects:

```java
PlayerDataCache<User> users = CotaniCache.players(User.class)
    .repository(new UserRepository(scheduler))
    .defaultValue(User::createDefault)
    .preset(CachePreset.PLAYER_DATA)
    .maximumConcurrentSaves(8)
    .invalidationBus(invalidationBus)
    .build(plugin, scheduler);
```

Without an invalidation bus, consistency is local/eventual. For multiple JVMs, provide a shared `CacheInvalidationBus` transport and require every writer to publish through it. Remote invalidation never discards a locally dirty value.

### 2. Mutating Cached Data Asynchronously

Safely mutate cached data asynchronously:

```java
// For immutable record models
users.updateAsync(playerId, user -> user.addCoins(100))
    .thenAccept(updatedUser -> {
        // Handle post-mutation actions
    });

// For mutable objects, mark dirty automatically
users.mutateAsync(playerId, user -> user.setCoins(200));
```

### 3. Generic Temporary Cache

Build a temporary cache for transient data (e.g. command cooldown timestamps):

```java
DataCache<UUID, Instant> cooldowns = CotaniCache.temporary(
    UUID.class, Instant.class, Duration.ofSeconds(30)
).defaultValue(Instant::now)
 .build(scheduler);
```

## Hard Rules & Best Practices

1. **Isolation of Repositories**: Implementations of `CacheRepository` must not access Bukkit/Paper APIs directly. All persistence calls must run on async executors.
2. **Default Value Factory**: Always declare a `defaultValue` provider. This ensures a fallback exists when data is missing or query failures occur during initialization.
3. **Shutdown Flushing**: Register the cache in the Cotani lifecycle or compose `cache.closeAsync()`. The synchronous adapter must not be called on the Paper main thread.
4. **Consistency Model**: Choose the correct mutation pattern: use `updateAsync` for immutable structures and `mutateAsync` for mutable data. Avoid mixing the two patterns arbitrarily on the same cache.

## Anti-Patterns

- Invoking `cache.get(...)` on keys that haven't been loaded. Use `getOrLoadAsync(...)` or check with `find(...)`.
- Implementing manual database save queries on every small change, overriding the automatic dirty tracking engine.
- Returning mutable collections directly from cached objects. Ensure you return immutable copies (e.g. `List.copyOf()`).

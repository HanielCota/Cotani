# cotani-cache

## Scope

In-memory caching with Caffeine, dirty tracking and async persistence for player and generic data.

## Hard rules

1. `CacheRepository` implementations must not access Bukkit/Paper APIs; they run on async executors.
2. Always provide a `defaultValue` factory when building a cache.
3. Call `cache.close()` on shutdown to save dirty entries.
4. Use `updateAsync` for immutable values and `mutateAsync` for mutable values; do not mix the two models arbitrarily.

## Patterns

### Player cache

```java
PlayerDataCache<User> users = CotaniCache.players(User.class)
    .repository(new UserRepository(scheduler))
    .defaultValue(User::createDefault)
    .preset(CachePreset.PLAYER_DATA)
    .build(this, scheduler);
```

### Update immutable value

```java
users.updateAsync(uuid, user -> user.addCoins(100))
    .thenAccept(updated -> { /* ... */ });
```

### Generic temporary cache

```java
DataCache<UUID, Instant> cooldowns = CotaniCache.temporary(
    UUID.class, Instant.class, Duration.ofSeconds(30)
).defaultValue(Instant::now)
 .build(scheduler);
```

## Anti-patterns

- Calling `cache.get(...)` on a key that has not been loaded.
- Saving manually on every small change instead of relying on dirty tracking/autosave.
- Returning mutable internal collections from cached values.

## Related skills

- `java-async-standards`
- `java-api-standards`

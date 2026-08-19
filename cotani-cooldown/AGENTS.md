# cotani-cooldown

## Scope

Thread-safe in-memory, cache-backed and SQL-distributed cooldown tracking with Paper events integration.

## Hard rules

1. Database-backed cooldown operations must use `DistributedCooldownService` asynchronously; never block the main thread.
2. Register `CotaniCooldowns.migrations()` before starting `CotaniStorage` when using distributed cooldowns.
3. Choose the appropriate consistency model: `inMemory()` for local ephemeral limits, `cacheBacked()` for player cache persistence, or `distributed()` for cross-server/multi-process synchronization.
4. Always close `DistributedCooldownService` on plugin shutdown to cancel periodic cleanup tasks.
5. Use immutable `CooldownKey`, `CooldownAction` and `CooldownTarget` value objects.

## Patterns

### In-memory cooldown check

```java
CooldownService cooldowns = CotaniCooldowns.inMemory();

CooldownResult result = cooldowns.user(userId)
    .action("chat.shout")
    .duration(Duration.ofSeconds(5))
    .checkAndStart();

if (result.denied()) {
    Duration remaining = result.remaining();
    player.sendMessage("Please wait " + remaining.toSeconds() + "s.");
}
```

### Cache-backed player cooldowns

```java
CooldownService cooldowns = CotaniCooldowns.cacheBacked(playerCooldownCache);

if (cooldowns.allow(userId, "daily.reward", Duration.ofHours(24))) {
    // grant reward
}
```

### Distributed SQL cooldown

```java
DistributedCooldownService distributed = CotaniCooldowns.distributed(storage, scheduler);

distributed.checkAndStartAsync(CooldownKey.user(userId, "global.vote"), Duration.ofHours(12))
    .thenAccept(result -> {
        if (result.allowed()) {
            // execute rewarded action across server cluster
        }
    });
```

## Anti-patterns

- Blocking on SQL queries inside Bukkit event listeners to check cooldowns.
- Re-triggering or duplicating cooldown starts within the same logical execution path.
- Using `inMemory()` when multiple server instances need to share atomic limits.

## Related skills

- `java-async-standards`
- `java-api-standards`
- `paper-plugin-architecture`

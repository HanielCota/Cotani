# cotani-redis

## Scope

Non-blocking Redis client, typed pub/sub messaging channels, and distributed locks for Paper and Folia server networks.

## Hard rules

1. Never block the caller thread (`join()`, `get()`, `Thread.sleep()`); always compose through `CompletionStage`.
2. Do not capture live Bukkit objects (`Player`, `World`, `Entity`) into Redis payloads; capture immutable identifiers (`UUID`, strings, value objects) and transition back to entity/region threads before touching Bukkit APIs.
3. Always provide a positive lease time when acquiring distributed locks to prevent cluster deadlocks if a server crashes.
4. Close `CotaniRedis` or register `CotaniRedisModule` inside `Cotani.forPlugin(...)` on plugin shutdown.
5. Use immutable `ChannelId`, `LockKey`, `LockToken`, and `RedisKey` value objects.

## Patterns

### Pub/Sub messaging

```java
RedisChannel<String> channel = redis.channel(ChannelId.of("network:alerts"));

channel.subscribe(alert -> {
    scheduler.global(() -> Bukkit.broadcast(Component.text(alert)));
});

channel.publishAsync("Server restarting in 5 minutes");
```

### Distributed lock with auto-release

```java
redis.locks().withLockAsync(LockKey.of("user:" + uuid), Duration.ofSeconds(5), () -> {
    return saveUserDataAsync(uuid);
});
```

## Anti-patterns

- Blocking on `pingAsync().join()` or `getAsync().toCompletableFuture().get()` inside Bukkit listeners or commands.
- Forgetting to unsubscribe or close channel subscriptions when listeners are disposed.
- Omitting lease time TTL on distributed locks.

## Related skills

- `java-async-standards`
- `java-api-standards`
- `paper-plugin-architecture`

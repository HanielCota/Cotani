# cotani-redis

Non-blocking Redis client, typed pub/sub messaging, and distributed locks for Paper and Folia plugin networks.

## Overview

`cotani-redis` provides composable, high-performance Redis integration for Minecraft server networks. Backed by Netty and Lettuce, all network operations are 100% non-blocking and return `CompletionStage<T>` without ever stalling the server tick.

## Features

- **Non-Blocking Architecture**: Zero thread blocking (`join()`, `get()`, or `sleep()`).
- **Typed Pub/Sub Channels**: Strongly typed messages with pluggable `RedisCodec<T>` (strings, raw bytes, JSON/records).
- **Distributed Locks**: Atomic mutual exclusion locks with TTL and Lua release script guaranteeing safe cross-server coordination.
- **Key-Value Store & Counters**: Atomic operations (`incrementAndGetAsync`, `setAsync` with TTL, `deleteAsync`).
- **Plugin Lifecycle Integration**: Seamless registration via `Cotani.forPlugin(plugin).with(CotaniRedisModule.create(plugin, config))`.

## Usage

### 1. Connecting to Redis

```java
RedisConfig config = RedisConfig.builder()
    .host("127.0.0.1")
    .port(6379)
    .password("secret")
    .timeout(Duration.ofSeconds(3))
    .build();

CotaniRedis redis = CotaniRedis.create(plugin, config, scheduler);

redis.startAsync().thenAccept(ignored -> {
    plugin.getLogger().info("Connected to Redis successfully!");
});
```

### 2. Pub/Sub Messaging

```java
RedisChannel<String> chatChannel = redis.channel(ChannelId.of("network:chat"));

// Subscribe to messages
ChannelSubscription subscription = chatChannel.subscribe(message -> {
    plugin.getLogger().info("Received cross-server message: " + message);
});

// Publish a message
chatChannel.publishAsync("Hello from Lobby-1!");
```

### 3. Distributed Mutual Exclusion Lock

```java
DistributedLockService locks = redis.locks();
LockKey key = LockKey.of("player:balance:" + playerId);

locks.withLockAsync(key, Duration.ofSeconds(5), () -> {
    return economyService.transferAsync(playerId, targetId, amount);
}).whenComplete((result, error) -> {
    if (error != null) {
        plugin.getLogger().warning("Could not execute transfer: " + error.getMessage());
    }
});
```

## Related Skills

- `java-async-standards`
- `java-api-standards`
- `paper-plugin-architecture`

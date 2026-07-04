# cotani-teleport

## Scope

Async teleport with policies, cooldowns, safe-location resolution and pending teleports.

## Hard rules

1. Use `TeleportRequest.builder()` with explicit `TeleportOptions` for every teleport.
2. Provide real `CombatAdapter` and `RegionProtectionAdapter` integrations in production; do not ship with noop defaults.
3. Handle both `TeleportResult.Success` and `TeleportResult.Failure` in the completion stage.
4. Use `CotaniTeleports.create(...)` instead of the deprecated static `CotaniTeleport` facade.
5. Capture `UUID` and `Location` (cloned) before async validation; do not pass live `Player` into policies.

## Patterns

### Basic teleport

```java
var module = CotaniTeleports.create(plugin, scheduler);
module.teleportService().teleport(
    TeleportRequest.builder()
        .playerId(player.getUniqueId())
        .target(location)
        .cause(TeleportCause.SPAWN)
        .source("spawn")
        .options(TeleportOptions.spawn())
        .build()
).thenAccept(result -> switch (result) {
    case TeleportResult.Success success -> player.sendMessage(Component.text("Teleportado!"));
    case TeleportResult.Failure failure -> player.sendMessage(Component.text("Falha: " + failure.reason()));
});
```

### Pending teleport

```java
module.pendingTeleportService().schedule(
    TeleportRequest.builder()
        .playerId(player.getUniqueId())
        .target(homeLocation)
        .cause(TeleportCause.HOME)
        .source("home")
        .options(TeleportOptions.defaults())
        .build(),
    Duration.ofSeconds(5)
);
```

## Anti-patterns

- Calling `player.teleport(...)` directly.
- Ignoring `TeleportResult.Failure`.
- Using noop adapters in production plugins.
- Holding `Player` references inside async teleport callbacks.

## Related skills

- `java-async-standards`
- `paper-plugin-architecture`

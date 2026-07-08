# cotani-teleport

Asynchronous teleport service for Paper. Supports teleport requests with delays, safety checks, cooldown managers, and customizable verification policies (combat, protection, etc.).

## Usage

```java
TeleportService teleports = module.teleportService();

teleports.teleport(
    TeleportRequest.builder()
        .playerId(player.getUniqueId())
        .target(location)
        .cause(TeleportCause.SPAWN)
        .source("cotani-spawn")
        .options(TeleportOptions.spawn())
        .build()
).thenAccept(result -> {
    // Handle TeleportResult.Success or Failure
});
```

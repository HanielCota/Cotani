<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-teleport

</div>

Asynchronous teleport service for Paper. Supports teleport requests with delays, safety checks, cooldown managers, and customizable verification policies (combat, protection, etc.).

## Overview

`cotani-teleport` provides an advanced, async-safe teleportation engine for PaperSpigot servers. It replaces basic `player.teleport()` calls with structured, state-machine-backed teleport sequences. It features safety verification (ensuring targets are not in lava, walls, or void), cooldown checking, and cancellation listeners if the player moves, takes damage, or enters combat during teleport delays.

## Features

- **Safe Location Resolver**: Scans target regions to guarantee players teleport to safe ground rather than lava, walls, or hazards.
- **Pending Teleports**: Schedule teleports with customizable countdown timers (e.g. 5-second warp delay).
- **Cancellation Policies**: Automatically cancels pending teleports if a player moves, takes damage, or quits the server.
- **Combat & Protection Adapters**: Integrate with external combat-tag and region-protection plugins through custom policy adapters.
- **Result Mappings**: Leverages Java's pattern matching to process `TeleportResult.Success` and `TeleportResult.Failure`.
- **Outcome Reconciliation**: A Paper future that never concludes is quarantined after the configured reconciliation deadline instead of keeping the player pipeline pending forever.

## Usage

### 1. Simple Teleportation

Initiate a teleport sequence and handle results:

```java
import com.cotani.teleport.api.CotaniTeleports;
import com.cotani.teleport.api.TeleportRequest;
import com.cotani.teleport.api.TeleportOptions;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportResult;

var module = CotaniTeleports.create(plugin, combatAdapter, regionAdapter, scheduler);
TeleportService teleports = module.teleportService();


teleports.teleportAsync(
    TeleportRequest.builder()
        .playerId(player.getUniqueId())
        .target(location.clone()) // Pass a cloned location copy
        .cause(TeleportCause.SPAWN)
        .source("spawn")
        .options(TeleportOptions.spawn()) // Configured safety checking policies
        .build()
).thenAccept(result -> {
    // Return to main thread via your scheduler before touching Paper objects
    scheduler.global(() -> {
        switch (result) {
            case TeleportResult.Success success -> 
                player.sendMessage(Component.text("Teleported successfully!"));
            case TeleportResult.Failure failure -> 
                player.sendMessage(Component.text("Teleport failed: " + failure.reason()));
        }
    });
});
```

### 2. Pending Teleport (Countdown Warp)

Set up a delayed teleport (e.g. 5 seconds countdown):

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

## Hard Rules & Best Practices

1. **Structured Teleport Requests**: Always create teleports through the `TeleportRequest` builder. Do not invoke `player.teleport(...)` directly.
2. **Production Adapters**: Implement and provide real integrations for `CombatAdapter` and `RegionProtectionAdapter` when deploying. Do not use the default noop placeholders in production.
3. **Handle Failure Paths**: Never ignore the completion stage return. Check for failure states to notify players or refund currency transactions.
4. **Instantiate via Factory**: Always create the service using `CotaniTeleports.create(plugin, combatAdapter, regionAdapter, scheduler)`.

5. **Entity Capturing Guidelines**: Capture `UUID` values and clone `Location` instances before starting validations. Do not pass mutable, live `Player` references down async policy stacks.
6. **Indeterminate Outcomes**: Handle `OUTCOME_INDETERMINATE` separately. While quarantined, `hasIndeterminateTeleport(playerId)` prevents another physical teleport. `releaseIndeterminateTeleport(playerId)` is an administrative acknowledgement that a late physical teleport may still occur.
7. **Reconciliation Deadline**: Configure `ExecutionSettings.builder().reconciliationTimeout(...)` according to operational latency; a late successful future is reconciled on the entity thread.

## Anti-Patterns

- Directly calling Bukkit's synchronous `player.teleport(...)` in asynchronous loops or streams.
- Swallowing failure states in completion stages, leaving players stuck in place without feedback.
- Retaining live `Player` references inside async tasks, causing memory leaks and thread-safety violations.

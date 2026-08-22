# cotani-region

## Scope

3D spatial regions, R-tree chunk grid containment queries, protection flags, and non-blocking enter/leave transition events for Paper and Folia.

## Hard rules

1. Register `CotaniRegions.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Construct immutable region definitions using `Region3D.builder(id, worldId)`.
3. Never perform synchronous database or file IO inside region protection listeners.
4. Precedence resolution evaluates higher priority regions first.

## Patterns

### Defining and Registering a Spawn Protection Region

```java
var spawnRegion = Region3D.builder("spawn", world.getUID())
    .name("<gold><bold>Spawn Hub</bold></gold>")
    .bounds(loc1, loc2)
    .priority(100)
    .flag(RegionFlag.PVP, false)
    .flag(RegionFlag.BLOCK_BREAK, false)
    .flag(RegionFlag.BLOCK_PLACE, false)
    .flag(RegionFlag.USE_CONTAINERS, true)
    .greeting("<green>Welcome to Spawn!</green>")
    .farewell("<red>You left the safe zone!</red>")
    .build();

regionModule.registerRegion(spawnRegion);
```

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`
- `java-engineering-standards`

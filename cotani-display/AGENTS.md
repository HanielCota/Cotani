# cotani-display

## Scope

Modern Display Entity engine for text, item, and block holograms on Paper and Folia.

## Hard rules

1. Register `CotaniDisplays.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Build holograms with `displays.holograms().builder(name)` and spawn with `spawnAsync(location)`.
3. Never manipulate raw `Display` entities across async threads; mutate lines via `hologram.updateLineAsync(...)` or region schedulers.
4. All spawned display entities are marked with `setPersistent(false)` automatically to prevent orphaned entities across server restarts.

## Patterns

### Text Hologram with Click Action

`java
var hologram = displays.holograms().builder("welcome")
    .addLine("<gold><bold>COTANI NETWORK</bold></gold>")
    .addLine("<gray>Click to open the menu</gray>")
    .onClick((player, holo, clickType) -> {
        player.sendMessage(Component.text("Opening menu..."));
    })
    .spawnAsync(location)
    .toCompletableFuture();
`

## Anti-patterns

- Direct manipulation of Bukkit `Display` entity objects from asynchronous threads.
- Spawning persistent entities that linger after plugin reload or shutdown.

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`

# cotani-hud

## Scope

Reactive, zero-flicker HUD module for Paper and Folia plugins providing dynamic Sidebars, TabList headers/footers, BossBars, and ActionBars.

## Hard rules

1. Register `CotaniHuds.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Build scoreboards with `hud.sidebar()` and apply to player with `.apply(player)`.
3. Mutate reactive properties (`State<T>`, `Property<T>`) or call sidebar update methods safely; updates are automatically dispatched to the player's entity thread on Folia and Paper.
4. Always close sidebars/bossbars or rely on `PlayerQuitEvent` automatic teardown.

## Patterns

### Reactive Scoreboard with Property Binding

```java
var coins = State.of(BigDecimal.ZERO);

var sidebar = hud.sidebar()
    .title(Component.text("COTANI NETWORK", NamedTextColor.GOLD))
    .line(15, Component.text("----------------", NamedTextColor.GRAY))
    .bindLine(14, coins, c -> Component.text("Coins: " + c, NamedTextColor.YELLOW))
    .line(13, Component.text("----------------", NamedTextColor.GRAY))
    .apply(player);
```

## Anti-patterns

- Re-creating new `Scoreboard` objectives repeatedly on every line change (causes flickering).
- Accessing or modifying Bukkit Scoreboard/Player objects asynchronously without scheduler entity thread transitions.

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`
- `java-engineering-standards`

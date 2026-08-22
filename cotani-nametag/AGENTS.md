# cotani-nametag

## Scope

Scoreboard-backed player nametag formatting, tablist sorting priority, and team options module for Paper and Folia.

## Hard rules

1. Register `CotaniNametags.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Construct immutable nametag instances using `Nametag.builder()` or `Nametag.of(...)`.
3. Apply tags via `nametagModule.apply(...)` or `nametagModule.applyForViewer(...)`; scoreboard updates are dispatched safely to viewer entity threads on Folia and Paper.
4. Never modify Scoreboard Teams directly on foreign threads; always route through `NametagModule`.

## Patterns

### Applying Global Rank Nametag

```java
var vipTag = Nametag.builder()
    .priority(50)
    .prefix("<gold>[VIP]</gold> ")
    .color(NamedTextColor.GOLD)
    .build();

nametagModule.apply(player, vipTag);
```

## Anti-patterns

- Manipulating Scoreboard Teams synchronously in async event listeners.
- Unregistering and recreating teams on every tick (causes nametag flicker and tab glitching).

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`
- `java-engineering-standards`

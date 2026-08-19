# cotani-gui

## Scope

Declarative, reactive, zero-flicker and anti-exploit-safe inventory GUI engine for Paper and Folia.

## Hard rules

1. Register `CotaniGuiModule.create(plugin)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Build menus with `GuiWindow.panel(...)` and ASCII `Structure` matrix layouts; never manipulate raw slots directly.
3. `GuiWindow.open(player)` and reactive `Property` mutations must run on the thread that owns the viewer (main thread on Paper, entity region thread on Folia).
4. `Property.Subscription` handles are disposed automatically by `GuiPanel.handleClose()`; do not leak long-lived observers.
5. All click events on Cotani top inventories are intercepted and cancelled by `AntiExploitGuard` with per-player debounce.

## Patterns

### Reactive Profile Panel

```java
var fly = State.of(profile.hasFlyEnabled());

GuiWindow.panel("Perfil")
    .rows(3)
    .structure(
        "# # # # # # # # #",
        "# . F . . . H . #",
        "# # # # # # # # #"
    )
    .border(Material.GRAY_STAINED_GLASS_PANE)
    .bindToggle('F', fly, Material.FEATHER, "Modo Voo", profile::setFly)
    .bind('H', Items.head(player, "<gold>" + player.getName()))
    .open(player);
```

## Anti-patterns

- Opening GUIs or mutating `Property` values from asynchronous threads.
- Bypassing `AntiExploitGuard` or manually unregistering inventory listeners.
- Mutating internal `Inventory` contents directly without going through `Property`/`Button`.

## Related skills

- `paper-plugin-architecture`
- `java-api-standards`
- `java-engineering-standards`

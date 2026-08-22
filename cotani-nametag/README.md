# cotani-nametag

Scoreboard-backed player nametag formatting, tablist sorting priority, and team options module for Paper and Folia.

## Overview

`cotani-nametag` manages above-head player nametags and tablist ordering without flickering or scoreboard conflicts:

- **⚡ Zero-Flicker Scoreboard Teams:** Dynamic prefix, suffix, and player name coloring using non-destructive team updates.
- **🔢 TabList Priority Sorting:** Deterministic team name sorting ensuring staff/VIP ranks appear first in the player list.
- **👥 Per-Viewer & Dynamic Overrides:** Support for global nametags, per-viewer customizations (e.g. friends, clans, bounties), and `NametagProvider` hooks.
- **🛡️ Team Options & Collision:** Built-in support for collision rules, nametag visibility policies, friendly fire, and friendly invisibles.
- **🧵 Folia & Paper Safe:** Automatic scheduling to player entity/region threads via `PaperTaskScheduler`.
- **🔄 Auto-Cleanup:** Complete teardown on `PlayerQuitEvent` and plugin shutdown.

---

## Installation

Add `cotani-nametag` to your build script:

```kotlin
dependencies {
    implementation("com.cotani:cotani-nametag")
}
```

---

## Bootstrap

Register the module once in your plugin's `onEnable`:

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private NametagModule nametagModule;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.nametagModule = CotaniNametags.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(nametagModule)
                .build();
    }

    @Override
    public void onDisable() {
        if (cotani != null) {
            cotani.closeAsync();
        }
    }
}
```

---

## Usage Examples

### 1. Applying a Global Nametag with Priority

```java
var adminTag = Nametag.builder()
    .priority(1) // Priority 1 sorts before Priority 1000 in TabList
    .prefix("<red><bold>[ADMIN]</bold></red> ")
    .suffix(" <gray>[Staff]</gray>")
    .color(NamedTextColor.RED)
    .visibility(NametagVisibility.ALWAYS)
    .collisionRule(CollisionRule.NEVER)
    .build();

nametagModule.apply(player, adminTag);
```

### 2. Per-Viewer Nametag Override

```java
// Viewer sees target with a special Friend tag
var friendTag = Nametag.builder()
    .priority(10)
    .prefix("<green>[Friend]</green> ")
    .color(NamedTextColor.GREEN)
    .build();

nametagModule.applyForViewer(viewer, target, friendTag);
```

### 3. Dynamic Resolution via NametagProvider

```java
nametagModule.registerProvider((viewer, target) -> {
    if (clanService.areAllies(viewer, target)) {
        return Optional.of(Nametag.builder()
            .prefix("<aqua>[Ally]</aqua> ")
            .color(NamedTextColor.AQUA)
            .friendlyFire(false)
            .build());
    }
    return Optional.empty(); // Falls back to player's global nametag
});
```

---

## API Summary

| Interface | Role |
| :--- | :--- |
| [`CotaniNametags`](src/main/java/com/cotani/nametag/CotaniNametags.java) | Entrypoint factory for creating `NametagModule` |
| [`NametagModule`](src/main/java/com/cotani/nametag/api/NametagModule.java) | Main service contract for managing nametags and teams |
| [`Nametag`](src/main/java/com/cotani/nametag/api/Nametag.java) | Immutable nametag record with fluent builder |
| [`NametagVisibility`](src/main/java/com/cotani/nametag/api/NametagVisibility.java) | Above-head nametag visibility policies |
| [`CollisionRule`](src/main/java/com/cotani/nametag/api/CollisionRule.java) | Entity collision rules |
| [`NametagProvider`](src/main/java/com/cotani/nametag/api/NametagProvider.java) | Functional SPI for dynamic per-viewer nametag resolution |

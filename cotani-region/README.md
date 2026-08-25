<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-region

</div>

High-performance 3D spatial region management, chunk grid containment queries, and protection flags for Paper and Folia.

## Overview

`cotani-region` provides lightweight, sub-millisecond 3D area management:

- **🌐 Chunk Grid Indexing:** Fast chunk-local candidate lookup across thousands of regions without scanning the full registry.
- **🛡️ Protection Flags:** Out-of-the-box support for `PVP`, `BLOCK_BREAK`, `BLOCK_PLACE`, `USE_DOORS`, `USE_CONTAINERS`, `ITEM_DROP`, `ITEM_PICKUP`, and `ENTRY`.
- **✨ Transition Titles & Messages:** Built-in Adventure MiniMessage greeting and farewell messages.
- **⚡ Priority Overrides:** Hierarchical region priority resolution for nested sub-zones (e.g. VIP shop inside spawn).
- **🧵 Folia & Paper Safe:** Thread-safe spatial queries and player region transitions.

---

## Installation

```kotlin
dependencies {
    implementation("com.cotani:cotani-region")
}
```

---

## Bootstrap

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private RegionModule regionModule;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.regionModule = CotaniRegions.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(regionModule)
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

## Usage Example

```java
var arenaRegion = Region3D.builder("arena-pvp", world.getUID())
    .name("<red><bold>PvP Arena</bold></red>")
    .bounds(pos1, pos2)
    .priority(50)
    .flag(RegionFlag.PVP, true)
    .flag(RegionFlag.BLOCK_BREAK, false)
    .flag(RegionFlag.BLOCK_PLACE, false)
    .greeting("<yellow>Entering combat zone!</yellow>")
    .farewell("<green>Entering safe area.</green>")
    .build();

regionModule.registerRegion(arenaRegion);
```

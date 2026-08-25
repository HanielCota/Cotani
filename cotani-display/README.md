<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-display

</div>

Modern, high-performance, non-blocking Display Entity and Hologram engine for Paper and Folia plugins.

## Overview

`cotani-display` replaces legacy, heavy `ArmorStand`-based holograms with native Minecraft 1.20+ / 1.21+ Display Entities (`TextDisplay`, `ItemDisplay`, `BlockDisplay`, and `Interaction`).

- **⚡ Native Display Entities:** High client rendering performance, customizable scaling, billboards, backgrounds, shadows, and view ranges.
- **🧵 Folia & Region-Thread Native:** All entity spawning, teleporting, updating, and despawning execute safely via `PaperTaskScheduler` on region threads.
- **🖱️ Clickable Hitboxes:** Built-in `Interaction` entity hitboxes with click debounce and left/right/shift click event dispatch.
- **✨ MiniMessage Integration:** Fluent line creation with Adventure components and MiniMessage strings.
- **🔄 Coordinated Teardown:** Automatic cleanup of all active holograms on plugin shutdown via `Cotani.forPlugin(...)`.

---

## Installation

Add `cotani-display` to your build script:

```kotlin
dependencies {
    implementation("com.cotani:cotani-display")
}
```

---

## Bootstrap

Register the module once in your plugin's `onEnable`:

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private DisplayModule displays;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.displays = CotaniDisplays.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(displays)
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

### 1. Creating a Floating Text Hologram

```java
Location spawnLocation = player.getLocation().add(0, 2, 0);

displays.holograms().builder("welcome_hologram")
    .billboard(DisplayBillboard.CENTER)
    .lineSpacing(0.28)
    .addLine("<gold><bold>WELCOME TO THE SERVER</bold></gold>")
    .addLine("<gray>Explore the worlds and have fun!</gray>")
    .addLine("<yellow>Click to open the server menu</yellow>")
    .onClick((clicker, hologram, clickType) -> {
        clicker.sendMessage(Component.text("Opening menu..."));
    })
    .spawnAsync(spawnLocation)
    .thenAccept(hologram -> {
        getLogger().info("Hologram spawned with ID: " + hologram.id());
    });
```

### 2. Multi-Layer Hologram with Floating Items & Blocks

```java
displays.holograms().builder("loot_crate_display")
    .addItemLine(new ItemStack(Material.DIAMOND_SWORD), 1.2f)
    .addLine("<aqua><bold>LEGENDARY CRATE</bold></aqua>")
    .addLine("<gray>Right-click with key to unlock</gray>")
    .onClick((clicker, hologram, clickType) -> {
        if (clickType.isRightClick()) {
            crateService.openCrate(clicker);
        }
    })
    .spawnAsync(chestLocation.add(0.5, 1.5, 0.5));
```

### 3. Dynamic Line Updates

```java
scheduler.global(() -> {
    int onlinePlayers = Bukkit.getOnlinePlayers().size();
    displays.holograms().find("welcome_hologram").ifPresent(hologram ->
        hologram.updateLineAsync(1, Component.text("Online players: " + onlinePlayers)));
});
```

---

## API Summary

| Interface | Role |
| :--- | :--- |
| [`CotaniDisplays`](src/main/java/com/cotani/display/CotaniDisplays.java) | Entrypoint factory for creating `DisplayModule` |
| [`HologramService`](src/main/java/com/cotani/display/api/HologramService.java) | Central registry and discovery for active holograms |
| [`HologramBuilder`](src/main/java/com/cotani/display/api/HologramBuilder.java) | Fluent builder for assembling text, item, and block layers |
| [`Hologram`](src/main/java/com/cotani/display/api/Hologram.java) | Spawned hologram entity instance with async mutation methods |
| [`DisplayBillboard`](src/main/java/com/cotani/display/api/DisplayBillboard.java) | Billboard orientation mode (`CENTER`, `FIXED`, `VERTICAL`, `HORIZONTAL`) |
| [`HologramClickHandler`](src/main/java/com/cotani/display/api/HologramClickHandler.java) | Click callback for interaction hitboxes |

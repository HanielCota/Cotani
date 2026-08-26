<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-hud

</div>

Reactive, zero-flicker HUD module for Paper and Folia plugins providing dynamic Sidebars, TabList headers/footers, BossBars, and ActionBars.

## Overview

`cotani-hud` delivers a modern presentation pipeline for player interfaces:

- **⚡ Zero-Flicker Sidebars:** Team-prefix scoreboards that update instantaneously without flickering or recreation.
- **🧵 Folia & Paper Safe:** Thread transitions to player entity threads are handled automatically via `PaperTaskScheduler`.
- **🔄 Reactive State Integration:** Binds lines, titles, progress, and action bars directly to `State<T>` and `Property<T>`.
- **📋 TabList Manager:** Dynamic player list headers and footers with single-call and reactive bindings.
- **👾 BossBar Engine:** Adventure `BossBar` wrapper with animated countdown timers and auto-cleanup.
- **💬 ActionBar Manager:** Timed and reactive action bar messages.
- **🛡️ Auto-Cleanup:** Automatically unbinds and cleans up all HUD elements on `PlayerQuitEvent` and plugin shutdown.

---

## Installation

Add `cotani-hud` to your build script:

```kotlin
dependencies {
    implementation("com.cotani:cotani-hud")
}
```

---

## Bootstrap

Register the module once in your plugin's `onEnable`:

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private HudModule hud;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.hud = CotaniHuds.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(hud)
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

### 1. Reactive Zero-Flicker Sidebar

```java
var kills = State.of(0);
var balance = State.of(BigDecimal.valueOf(1500.50));

var sidebar = hud.sidebar()
    .title(Component.text("COTANI NETWORK", NamedTextColor.GOLD))
    .line(15, Component.text("-------------------", NamedTextColor.GRAY))
    .line(14, player -> Component.text("Player: " + player.getName(), NamedTextColor.YELLOW))
    .bindLine(13, kills, k -> Component.text("Kills: " + k, NamedTextColor.RED))
    .bindLine(12, balance, b -> Component.text("Balance: $" + b, NamedTextColor.GREEN))
    .line(11, Component.text("-------------------", NamedTextColor.GRAY))
    .line(10, Component.text("play.cotani.net", NamedTextColor.YELLOW))
    .apply(player);

// Updating the reactive property automatically updates the player's scoreboard line:
kills.set(1);
```

### 2. Dynamic TabList Header & Footer

```java
hud.tabList().setHeaderAndFooter(
    player,
    Component.text("Welcome to Cotani!\nOnline: " + Bukkit.getOnlinePlayers().size(), NamedTextColor.AQUA),
    Component.text("\nstore.cotani.net · discord.gg/cotani", NamedTextColor.YELLOW)
);
```

### 3. Animated BossBar with Countdown

```java
var bossBar = hud.bossBar().builder()
    .title(Component.text("PvP Event starting in 30s...", NamedTextColor.RED))
    .color(BossBar.Color.RED)
    .overlay(BossBar.Overlay.NOTCHED_10)
    .progress(1.0f)
    .countdown(Duration.ofSeconds(30))
    .show(player);
```

### 4. Timed Action Bar

```java
hud.actionBar().sendTimed(player, Component.text("+100 Coins received!", NamedTextColor.GREEN), Duration.ofSeconds(3));
```

---

## API Summary

| Interface | Role |
| :--- | :--- |
| [`CotaniHuds`](src/main/java/com/cotani/hud/CotaniHuds.java) | Entrypoint factory for creating `HudModule` |
| [`Sidebar`](src/main/java/com/cotani/hud/api/Sidebar.java) | Active per-player sidebar interface |
| [`SidebarBuilder`](src/main/java/com/cotani/hud/api/SidebarBuilder.java) | Fluent builder for assembling reactive scoreboards |
| [`TabListController`](src/main/java/com/cotani/hud/api/TabListController.java) | Player list header and footer manager |
| [`BossBarController`](src/main/java/com/cotani/hud/api/BossBarController.java) | Adventure BossBar builder and registry |
| [`ActionBarController`](src/main/java/com/cotani/hud/api/ActionBarController.java) | Action bar presentation manager |

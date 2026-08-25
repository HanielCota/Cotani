<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-placeholder

</div>

High-performance placeholder parsing engine, custom expansion registry, and bidirectional bridge for Paper and Folia plugins.

## Overview

`cotani-placeholder` provides a high-throughput, non-blocking placeholder engine designed for modern Minecraft server environments:

- **⚡ Fast Parsing Engine:** Single-pass token scanning and replacing supporting both `{token}` and `%token%` formats with zero regex overhead.
- **🧩 Custom Expansions:** Register simple lambda handlers, structured expansions, asynchronous handlers (`CompletionStage`), and relational placeholders.
- **🌐 Automatic PlaceholderAPI Bridge:** Seamlessly bridges with PlaceholderAPI (PAPI) if present on the server without introducing a hard runtime dependency.
- **✨ Adventure MiniMessage Integration:** Native support for formatting text to `Component` and generating dynamic Adventure `TagResolver` instances.
- **🧵 Async & Thread Safe:** Safely parses placeholders on any thread (Paper main thread, Folia entity region threads, and async workers).

---

## Installation

Add `cotani-placeholder` to your build script:

```kotlin
dependencies {
    implementation("com.cotani:cotani-placeholder")
}
```

---

## Bootstrap

Register the module in your plugin's `onEnable`:

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private PlaceholderService placeholders;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.placeholders = CotaniPlaceholders.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(placeholders)
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

### 1. Registering Custom Placeholders

```java
// Simple synchronous placeholder
placeholders.register("coins", (ctx, params) -> {
    UUID uuid = ctx.viewerId();
    return uuid != null ? String.valueOf(economyService.getBalance(uuid)) : "0";
});

// Asynchronous non-blocking placeholder
placeholders.registerAsync("rank", (ctx, params) -> {
    UUID uuid = ctx.viewerId();
    return uuid == null
        ? CompletableFuture.completedFuture("default")
        : permissionService.getPrimaryGroupAsync(uuid);
});

// Relational placeholder comparing two players
placeholders.registerRelational("clan", (viewer, target, params) -> {
    return clanService.areAllies(viewer.getUniqueId(), target.getUniqueId()) ? "ALLIED" : "NEUTRAL";
});
```

### 2. Parsing Strings and Text Templates

```java
// Synchronous resolution (supports both {coins} and %coins%)
String message = placeholders.parse(player, "Hello {player_name}, you have {coins} coins!");

// Asynchronous non-blocking resolution
UUID playerId = player.getUniqueId();
placeholders.parseAsync(playerId, "Welcome {player_name}! Rank: {rank}").thenAccept(result -> {
    scheduler.entity(playerId, () -> {
        Player current = Bukkit.getPlayer(playerId);
        if (current != null) {
            current.sendMessage(Component.text(result));
        }
    });
});

// Relational resolution
String relationText = placeholders.parseRelational(viewer, target, "Status: %rel_clan%");
```

### 3. Adventure MiniMessage & Components

```java
// Parse directly into an Adventure Component
Component component = placeholders.parseComponent(player, "<green>Player: <yellow>{player_name}</yellow> | Coins: <gold>{coins}</gold>");

// Or use as a dynamic TagResolver in MiniMessage
TagResolver resolver = placeholders.tagResolver(PlaceholderContext.of(player));
Component parsed = MiniMessage.miniMessage().deserialize("<player> has <coins> coins!", resolver);
```

---

## API Summary

| Interface | Role |
| :--- | :--- |
| [`CotaniPlaceholders`](src/main/java/com/cotani/placeholder/CotaniPlaceholders.java) | Entrypoint factory for creating `PlaceholderService` |
| [`PlaceholderService`](src/main/java/com/cotani/placeholder/api/PlaceholderService.java) | Main service managing registration, parsing, and bridges |
| [`PlaceholderContext`](src/main/java/com/cotani/placeholder/api/PlaceholderContext.java) | Immutable context capturing viewer, target, and parameters |
| [`PlaceholderExpansion`](src/main/java/com/cotani/placeholder/api/PlaceholderExpansion.java) | Modular expansion contract |
| [`RelationalPlaceholderExpansion`](src/main/java/com/cotani/placeholder/api/RelationalPlaceholderExpansion.java) | Relational expansion contract comparing two players |

# Cotani

A modular, async-safe library for modern Paper/PaperSpigot plugin development in Java.

## Modules

- **`cotani-core`**: Core lifecycle bootstrap and utilities.
- **`cotani-task`**: Async-first task scheduling, `TaskChain`, and Folia support.
- **`cotani-cache`**: Explicit caching with Caffeine and automated dirty tracking.
- **`cotani-config`**: YAML-to-record config binding, reloadable registries, and validation.
- **`cotani-storage`**: Database engine supporting SQLite, MySQL, and MariaDB with automatic migrations.
- **`cotani-text`**: MiniMessage utilities and streamlined Adventure component formatting.
- **`cotani-item`**: Fluent `ItemStack` and texture resolving builders.
- **`cotani-user`**: User lifecycle, cache, and service manager.
- **`cotani-economy`**: Safe `BigDecimal` transaction-based economy service.
- **`cotani-teleport`**: Async teleportation requests, safety checks, and cooldowns.
- **`cotani-cooldown`**: Database-persistent non-blocking cooldown manager.
- **`cotani-event`**: Async-safe event bus, priority listeners, and subscriptions.

## Usage

### JitPack

Configure your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.HanielCota.Cotani:cotani-core:1.0.0")
    implementation("com.github.HanielCota.Cotani:cotani-task:1.0.0")
    // Include other modules as needed
}
```

### Gradle Commands

- **Build**: `./gradlew build`
- **Verify & Test**: `./gradlew check`
- **Publish to Maven Local**: `./gradlew publishToMavenLocal`

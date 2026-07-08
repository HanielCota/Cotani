# Cotani

A modular, async-safe library for modern Paper/PaperSpigot plugin development in Java.

---

## Overview

Cotani is an architectural foundation for high-performance Minecraft server plugins. It enforces **non-blocking async execution patterns**, protects server region threads from heavy operations (such as databases and configuration reads), and implements explicit thread transitions using PaperSpigot's modern APIs (including Folia support).

---

## Modules Directory

Cotani is composed of 12 decoupled submodules. Explore each module's detailed documentation:

| Module Name | Purpose & Scope | Link |
| :--- | :--- | :--- |
| **`cotani-core`** | Bootstrapping, ordered service shutdown, and shared exception models. | [Core README](file:///D:/Cotani/cotani-core/README.md) |
| **`cotani-task`** | Async task execution, exponential retry policies, and thread switching (`TaskChain`). | [Task README](file:///D:/Cotani/cotani-task/README.md) |
| **`cotani-cache`** | Caffeine-backed caching with automated dirty state tracking and background saves. | [Cache README](file:///D:/Cotani/cotani-cache/README.md) |
| **`cotani-config`** | Immutable configuration mapping from YAML directly into Java `record`s with validation tags. | [Config README](file:///D:/Cotani/cotani-config/README.md) |
| **`cotani-storage`** | Off-thread SQL driver engine (SQLite/MySQL/MariaDB) with automated migrations and transactions. | [Storage README](file:///D:/Cotani/cotani-storage/README.md) |
| **`cotani-text`** | MiniMessage string formatting utilities, dynamic placeholder structures, and audience sending. | [Text README](file:///D:/Cotani/cotani-text/README.md) |
| **`cotani-item`** | Modern fluent builders for `ItemStack` (weapons, armors, and player skulls) mapping MiniMessage. | [Item README](file:///D:/Cotani/cotani-item/README.md) |
| **`cotani-user`** | Player session management, asynchronous joined-data resolution, and cache flushes. | [User README](file:///D:/Cotani/cotani-user/README.md) |
| **`cotani-economy`** | exact-precision `BigDecimal` transaction handling with idempotency tokens. | [Economy README](file:///D:/Cotani/cotani-economy/README.md) |
| **`cotani-teleport`** | Async teleport sequences with target safety scanning, countdown delay, and cancel triggers. | [Teleport README](file:///D:/Cotani/cotani-teleport/README.md) |
| **`cotani-cooldown`** | Database persistent and temporary in-memory action cooldown tracking. | [Cooldown README](file:///D:/Cotani/cotani-cooldown/README.md) |
| **`cotani-event`** | Reflection-free, prioritized event publishing bus with subscription controls. | [Event README](file:///D:/Cotani/cotani-event/README.md) |

---

## Core Architecture Guidelines

All modules in this project are designed to align with strict performance and structural guidelines defined in the repository's rules.

- **Non-Blocking Execution**: Blocking the server's main thread with operations like `future.join()`, `future.get()`, or `Thread.sleep(...)` is strictly forbidden.
- **Entity Isolation**: Mutable Minecraft handles (such as `Player`, `World`, or `Entity`) must not be stored in long-lived variables or captured inside async lamdas.
- **Constructor Injection**: All classes must resolve dependencies through constructor injection. Global singletons and service locators are avoided.
- **Null Safety**: Methods must return Java `Optional` structures rather than returning `null` references directly.

For a complete breakdown of coding standards, refer to our [AGENTS.md](file:///D:/Cotani/AGENTS.md) rules file.

---

## Setup & Usage

### JitPack Configuration

Include Cotani in your project's `build.gradle.kts` file:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // Import the bootstrapping core
    implementation("com.github.HanielCota.Cotani:cotani-core:1.0.0")
    
    // Import task executors and schedulers
    implementation("com.github.HanielCota.Cotani:cotani-task:1.0.0")
    
    // Import other modules as needed
    implementation("com.github.HanielCota.Cotani:cotani-cache:1.0.0")
}
```

### Gradle Tasks

Build and verify the repository code using standard Gradle operations:

- **Build Output**: `./gradlew build`
- **Verify Quality**: `./gradlew check`
- **Local Publish**: `./gradlew publishToMavenLocal`

---

## Technical Standards Skills

To maintain code standards across this project, developers should refer to the localized agent skill guidelines:
- [Java Engineering Standards](file:///D:/Cotani/.agents/skills/java-engineering-standards/SKILL.md)
- [Java Async Standards](file:///D:/Cotani/.agents/skills/java-async-standards/SKILL.md)
- [Java API Standards](file:///D:/Cotani/.agents/skills/java-api-standards/SKILL.md)
- [Paper Plugin Architecture](file:///D:/Cotani/.agents/skills/paper-plugin-architecture/SKILL.md)

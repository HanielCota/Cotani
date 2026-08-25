<div align="center">

<img src="logo.png" alt="Cotani logo" width="320">

# Cotani

**Composable, high-performance infrastructure for safe, non-blocking Paper and Folia plugins.**

Build scalable Minecraft plugins with explicit execution boundaries, zero-reflection events, reactive GUIs, and robust persistence.

[![Build](https://img.shields.io/github/actions/workflow/status/HanielCota/Cotani/ci.yml?branch=master&style=flat-square&logo=github&label=build)](https://github.com/HanielCota/Cotani/actions/workflows/ci.yml)
[![Java 25](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Paper 26.2](https://img.shields.io/badge/Paper-26.2-3f48cc?style=flat-square)](https://papermc.io/)
[![Javadoc](https://img.shields.io/badge/docs-javadoc-0969da?style=flat-square&logo=gitbook)](https://hanielcota.github.io/Cotani/api/)
[![JitPack](https://jitpack.io/v/HanielCota/Cotani.svg)](https://jitpack.io/#HanielCota/Cotani)
[![MIT](https://img.shields.io/github/license/HanielCota/Cotani?style=flat-square)](LICENSE)

[English](README.md) · [Português](README.pt-BR.md)

[Overview](#overview) · [Compatibility](#compatibility) · [Installation](#installation) · [Modules](#choose-your-modules) · [Architecture](#architecture) · [Quick start](#five-minute-quick-start) · [Troubleshooting](#troubleshooting) · [Documentation](#documentation)

---

</div>

## Overview

Cotani is a modern Java 25 multi-module framework designed for Paper and Folia plugin development. It replaces fragile global state and main-thread blocking with explicit thread transitions, composable `CompletionStage` APIs, and clean domain isolation.

| Concern | Cotani Approach |
| :--- | :--- |
| **🧵 Thread Ownership** | Global, region, and entity transitions managed through `PaperTaskScheduler` and `TaskChain` |
| **⚡ Asynchronous Work** | Composable `CompletionStage` APIs with explicit executors — no hidden blocking or `join()` |
| **💾 Persistence** | SQLite, MySQL, and MariaDB drivers with automated schema migrations and transaction runners |
| **🧠 State & Caching** | Caffeine-backed caches with automatic dirty tracking, bulk flushing, and invalidation contracts |
| **🔄 Plugin Lifecycle** | Centralized ownership, reverse-order disposal, and non-blocking shutdown of registered resources |
| **🛡️ API Quality** | Immutable records, strict null-safety, and isolated implementation packages |

---

## Compatibility

| Cotani Version | Java Version | Paper API | Release Status | Included Modules |
| :--- | :---: | :---: | :--- | :--- |
| `1.1.1` | 25 | 26.2 | Stable tag on JitPack | All published modules |
| `1.0.0` | 25 | 26.2 | Legacy tag on JitPack | Core, task, text, item, config, storage, cache, user, economy, cooldown, teleport, event |

---

## Installation

### Stable Release (`1.1.1`)

Add PaperMC and JitPack to your build script repositories, then declare only the top-level modules your plugin needs:

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    val cotaniVersion = "1.1.1"

    implementation("com.github.HanielCota.Cotani:cotani-task:$cotaniVersion")
    implementation("com.github.HanielCota.Cotani:cotani-storage:$cotaniVersion")
}
```

Gradle automatically resolves internal Cotani dependencies transitively (e.g. `cotani-core` is pulled in when `cotani-task` is declared).

### BOM Alignment

To align versions across all Cotani modules using the Bill of Materials (BOM), publish the snapshot locally:

```bash
./gradlew publishToMavenLocal
```

Then consume the aligned BOM in your plugin:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(platform("com.cotani:cotani-bom:1.1.1"))
    implementation("com.cotani:cotani-task")
    implementation("com.cotani:cotani-storage")
    implementation("com.cotani:cotani-gui")
}
```

> [!IMPORTANT]
> Cotani modules are libraries, not standalone server plugins. Shade and relocate `com.cotani` (and `net.cotani` if using metrics) into your plugin's private namespace using Gradle Shadow.

---

## Choose Your Modules

Declare only the modules required for your feature set; transitive dependencies are included automatically.

### 🧱 Foundation & Execution

| Module | Capability | Availability |
| :--- | :--- | :---: |
| [`cotani-core`](cotani-core/README.md) | Centralized plugin lifecycle and safe resource disposal | `1.1.1` |
| [`cotani-task`](cotani-task/README.md) | Async, global, region, and entity scheduling with fluent `TaskChain` | `1.1.1` |
| [`cotani-text`](cotani-text/README.md) | MiniMessage parsing, audience messaging, and placeholder resolvers | `1.1.1` |
| [`cotani-locale`](cotani-locale/README.md) | Player locale preferences, fallback catalogs, and safe MiniMessage rendering | `1.1.1` |
| [`cotani-punishment`](cotani-punishment/README.md) | Immutable async bans, mutes, warnings, expiration, revocation, and audit integration | `1.1.1` |
| [`cotani-location`](cotani-location/README.md) | Immutable homes and warps with asynchronous persistence and safe teleport integration | `1.1.1` |
| [`cotani-mail`](cotani-mail/README.md) | Persistent player mail with TTL, idempotent sends, inbox pagination, and SQL persistence | `1.1.1` |
| [`cotani-reward`](cotani-reward/README.md) | Persistent rewards with cooldowns, streaks, idempotent claims, immutable grants, and SQL persistence | `1.1.1` |
| [`cotani-reward-integration`](cotani-reward-integration/README.md) | Economy and entity-thread-safe inventory settlement adapters for rewards | `1.1.1` |
| [`cotani-party`](cotani-party/README.md) | Async parties with expiring invitations, roles, leadership transfer, and persistence SPI | `1.1.1` |
| [`cotani-friend`](cotani-friend/README.md) | Async friendships, requests, blocks, optimistic persistence, and domain events | `1.1.1` |
| [`cotani-queue`](cotani-queue/README.md) | Async priority queues, expiring tickets, capacity limits, and atomic matchmaking | `1.1.1` |
| [`cotani-trade`](cotani-trade/README.md) | Confirmation-based player trading with immutable offers and idempotent settlement | `1.1.1` |
| [`cotani-item`](cotani-item/README.md) | Fluent Paper 1.21+ data-component item, armor, and skull builders | `1.1.1` |

### ⚙️ Infrastructure & Persistence

| Module | Capability | Availability |
| :--- | :--- | :---: |
| [`cotani-config`](cotani-config/README.md) | YAML binding to immutable records with constraint validation & async reload | `1.1.1` |
| [`cotani-storage`](cotani-storage/README.md) | SQLite, MySQL, and MariaDB queries, schema migrations, and transactions | `1.1.1` |
| [`cotani-cache`](cotani-cache/README.md) | Caffeine-backed caches with automatic dirty-tracking and persistence | `1.1.1` |
| [`cotani-redis`](cotani-redis/README.md) | Non-blocking Redis client, pub/sub messaging, distributed locks & sync | `1.1.1` |
| [`cotani-permission`](cotani-permission/README.md) | Async permission nodes, groups, inheritance decisions, and SQL persistence | `1.1.1` |
| [`cotani-inventory`](cotani-inventory/README.md) | Binary inventory snapshots, rollback, and cross-server transfer locks | `1.1.1` |

### 🎮 Gameplay & Domain Systems

| Module | Capability | Availability |
| :--- | :--- | :---: |
| [`cotani-user`](cotani-user/README.md) | Async user profile loading, online cache, and session management | `1.1.1` |
| [`cotani-economy`](cotani-economy/README.md) | Exact `BigDecimal` economy, atomic transactions, and idempotency guarantees | `1.1.1` |
| [`cotani-cooldown`](cotani-cooldown/README.md) | Local and distributed SQL-backed cooldown limits with automatic pruning | `1.1.1` |
| [`cotani-teleport`](cotani-teleport/README.md) | Policy-driven teleport pipelines with hazard checks, combat tags & delays | `1.1.1` |
| [`cotani-event`](cotani-event/README.md) | Reflection-free, high-performance event bus with priority dispatching | `1.1.1` |
| [`cotani-gui`](cotani-gui/README.md) | Reactive inventory UIs with declarative structure, pagination & anti-dupe guards | `1.1.1` |
| [`cotani-display`](cotani-display/README.md) | Modern Display Entity engine for text, item, and block holograms | `1.1.1` |
| [`cotani-command`](cotani-command/README.md) | Declarative command framework with async arguments, cooldowns, and Folia thread safety | `1.1.1` |
| [`cotani-hud`](cotani-hud/README.md) | Reactive zero-flicker scoreboards, dynamic tablist, bossbars, and actionbars | `1.1.1` |
| [`cotani-nametag`](cotani-nametag/README.md) | Scoreboard team-driven nametag formatting, tablist sorting priority, and collision rules | `1.1.1` |
| [`cotani-npc`](cotani-npc/README.md) | Virtual packet-based player NPCs with dynamic look-at, skins, equipment, and click raycasting | `1.1.1` |
| [`cotani-region`](cotani-region/README.md) | 3D spatial regions, chunk grid indexer, protection flags, and transition events | `1.1.1` |
| [`cotani-dialog`](cotani-dialog/README.md) | Non-blocking reactive chat, sign, and anvil input prompts and multi-step wizards | `1.1.1` |
| [`cotani-placeholder`](cotani-placeholder/README.md) | Async-safe placeholder expansion, MiniMessage integration, and PlaceholderAPI bridge | `1.1.1` |

### 📊 Operations & Tooling

| Module | Capability | Availability |
| :--- | :--- | :---: |
| [`cotani-audit`](cotani-audit/README.md) | Immutable append-only audit trail with bounded async queries | `1.1.1` |
| [`cotani-audit-storage`](cotani-audit-storage/README.md) | Indexed, idempotent SQL persistence adapter for audit events | `1.1.1` |
| [`cotani-metrics`](cotani-metrics/README.md) | Micrometer metrics collector with optional Prometheus HTTP export | `1.1.1` |
| [`cotani-bom`](cotani-bom/README.md) | Bill of Materials for version alignment across all Cotani modules | `1.1.1` |

---

## Architecture

Cotani is organized into clean architectural layers. Feature modules compose infrastructure and foundation components instead of relying on mutable global singletons.

```mermaid
flowchart TB
    Plugin["Your Paper / Folia Plugin"]
    Features["Gameplay & Domain<br/>user · economy · teleport · cooldown · event · gui · punishment · metrics"]
    Infrastructure["Infrastructure<br/>config · storage · cache"]
    Foundation["Foundation<br/>core · task · text · item · locale"]
    Runtime["Paper / Folia Runtime"]

    Plugin --> Features
    Plugin --> Infrastructure
    Plugin --> Foundation
    Features -->|compose| Infrastructure
    Features -->|use| Foundation
    Infrastructure -->|use| Foundation
    Foundation -->|respect thread ownership| Runtime
```

Read the [complete architecture reference](docs/architecture.md) for the full dependency graph and thread-boundary sequence diagrams.

---

## Five-Minute Quick Start

This walkthrough sets up a shaded Paper plugin that reads player data asynchronously and safely returns to the player's entity thread before modifying game state.

### 1. Configure Gradle

`build.gradle.kts`:

```kotlin
plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.example"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    implementation("com.github.HanielCota.Cotani:cotani-task:e2f91df")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.cotani", "com.example.cotaniquickstart.libs.cotani")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
```

### 2. Describe the Plugin

`src/main/resources/plugin.yml`:

```yaml
name: CotaniQuickStart
version: '0.1.0'
main: com.example.cotaniquickstart.CotaniQuickStartPlugin
description: Minimal Cotani example
api-version: '26.2'
commands:
  cotanihello:
    description: Confirms that Cotani is running
    usage: /cotanihello
```

### 3. Add the Plugin Bootstrap

Use the compile-checked [`CotaniQuickStartPlugin`](docs-examples/src/main/java/com/example/cotaniquickstart/CotaniQuickStartPlugin.java). It demonstrates thin lifecycle ownership, constructor injection, and safe entity task transitions:

```java
public final class CotaniQuickStartPlugin extends JavaPlugin {

    private Cotani cotani;
    private PaperTaskScheduler scheduler;

    @Override
    public void onEnable() {
        this.scheduler = SchedulerFactory.create(this);
        this.cotani = Cotani.forPlugin(this)
            .withAsync(scheduler::closeAsync)
            .build();
    }

    @Override
    public void onDisable() {
        if (cotani != null) {
            cotani.closeAsync().exceptionally(error -> {
                getLogger().log(Level.SEVERE, "Error shutting down Cotani", error);
                return null;
            });
        }
    }
}
```

### 4. Async to Entity-Thread Transition

When an asynchronous query or service completes, retain the player `UUID` and transition back to the entity thread using `TaskChain`:

```java
UUID playerId = player.getUniqueId();
CompletionStage<String> messageStage = messageService.loadAsync(playerId);

scheduler.chain(messageStage)
    .consumeEntity(playerId, message -> {
        // Safe to interact with Bukkit/Paper objects on the player thread
        var onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(Component.text(message));
        }
    })
    .toCompletionStage()
    .whenComplete((_, failure) -> {
        if (failure != null) {
            logger.log(Level.SEVERE, "Could not message player " + playerId, failure);
        }
    });
```

> [!WARNING]
> Never call `join()`, `get()`, or `Thread.sleep(...)` in server code. Never carry live `Player`, `World`, `Entity`, `Inventory`, or `Block` objects across asynchronous boundaries.

---

## Troubleshooting

| Symptom | Likely Cause | Solution |
| :--- | :--- | :--- |
| JitPack cannot resolve `1.1.1` | Repository metadata or the release tag is not available to the resolver | Verify the JitPack repository is declared, retry with `v1.1.1`, or publish locally with `publishToMavenLocal` |
| `NoClassDefFoundError: com/cotani/...` | Unshaded jar deployed | Build and deploy the output of `shadowJar` with relocate configured |
| Async-catcher or wrong-thread exception | Bukkit API accessed in async lambda | Capture `UUID`s and return via `scheduler.chain(...).consumeEntity(...)` |
| Server stalls during command execution | Blocking call (`join()`, `get()`, I/O) on main thread | Compose with `CompletionStage`; eliminate synchronous database/file calls |
| GUI opens but click actions are ignored | `CotaniGuiModule` was not registered | Register `CotaniGuiModule.create(plugin)` inside `Cotani.forPlugin(plugin)` |
| Database integration tests do not start | Docker is unavailable | Start Docker daemon and ensure `docker info` succeeds before running `./gradlew integrationTest` |

---

## Documentation & Resources

- 🌐 **[Cotani Wiki](https://github.com/HanielCota/Cotani/wiki)** — Official guides, concepts, module map, and contribution standards
- 📖 **[Cotani Cookbook](docs/ai/cotani-cookbook.md)** — Copy-paste recipes for common Paper plugin patterns
- 🗂️ **[Documentation Index](docs/README.md)** — Maintained guides, validation commands, and documentation policy
- 🧩 **[API Documentation (Javadoc)](https://hanielcota.github.io/Cotani/api/)** — Aggregated Javadoc reference for all modules
- 💡 **[Showcase Plugin Examples](docs-examples/src/main/java/com/cotani/examples/showcase/ShowcasePlugin.java)** — Full compile-checked reference plugin implementation
- 🏗️ **[Architecture Reference](docs/architecture.md)** — Gradle dependency graphs and execution boundaries
- 📜 **[Asynchronous API Contracts](docs/async-contracts.md)** — Non-blocking guarantees and error propagation rules
- 🔄 **[1.x Migration Notes](docs/migration-1.x.md)** — Source-compatible upgrade guides

---

## Development

Clone the repository and run all checks using the included Gradle wrapper:

```bash
git clone https://github.com/HanielCota/Cotani.git
cd Cotani
./gradlew spotlessApply
./gradlew check
```

`check` validates unit tests, code formatting via Palantir, Error Prone, NullAway, compile-checked documentation examples, and module architectural boundaries. Docker-backed database suites are run separately:

```bash
./gradlew integrationTest
```

Review our [Contributing Guide](CONTRIBUTING.md), [Security Policy](SECURITY.md), and [Engineering Rules](AGENTS.md) before submitting contributions.

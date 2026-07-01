# 🧩 Cotani

> A modern, modular toolkit for Paper/Spigot plugin development on **Java 25**.

[![Java](https://img.shields.io/badge/Java-25-ea2d2f?logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-1.26.2+-1f8cb6?logo=minecraft)](https://papermc.io/)
[![Gradle](https://img.shields.io/badge/Gradle-9.6+-02303a?logo=gradle)](https://gradle.org/)

Cotani helps you build high-performance Minecraft plugins with less boilerplate. It bundles **fluent builders**, **Adventure text utilities**, **async task chaining**, and a **centralized lifecycle** into small, focused Gradle modules. Pick only what you need.

---

## ✨ Why Cotani?

Writing Paper plugins usually means juggling dozens of small concerns: formatting text, building items, scheduling tasks, closing resources, retrying I/O, and keeping the main thread happy. Cotani unifies those patterns into a clean, type-safe API designed for modern Java.

- 🧱 **Modular** — consume only the modules your plugin needs.
- 🧵 **Async-first** — virtual threads, `TaskChain`, and Paper schedulers working together.
- 🔒 **Null-safe** — every public package is `@NullMarked` and checked with **NullAway**.
- 🧹 **Clean code** — strict formatting, early returns, and warnings treated as errors.
- 🧪 **Tested** — unit tests with JUnit 5 and Mockito for public APIs.

---

## 🚀 Requirements

| Tool | Version |
|------|---------|
| Java | `25` |
| Gradle | `9.6+` |
| Paper API | `1.26.2+` |

---

## 📦 Modules

```
cotani/
├── 🏗️ cotani-core      # Plugin lifecycle and shared contracts
├── 💬 cotani-text      # MiniMessage, placeholders, and Adventure helpers
├── ⚔️ cotani-item      # Fluent ItemStack builders with data components
├── 🗄️ cotani-storage   # Async SQL storage with migrations and repositories
├── ⏱️ cotani-task      # TaskChain, async executors, and virtual threads
└── 🌀 cotani-teleport  # Safe, async-aware teleport API for Paper
```

### 🏗️ `cotani-core`

The heart of Cotani. Provides the `Cotani` lifecycle manager and shared contracts used by every other module.

Key class: `com.cotani.Cotani`

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;

    @Override
    public void onEnable() {
        cotani = Cotani.forPlugin(this).build();
    }

    @Override
    public void onDisable() {
        cotani.close(); // closes every registered resource
    }
}
```

### 💬 `cotani-text`

Everything you need to build, parse, and send rich Adventure components.

| Class | Purpose |
|-------|---------|
| `MiniMessages` | Parse MiniMessage strings into `Component`. |
| `ComponentTexts` | Convert components to MiniMessage, plain text, or legacy. |
| `ComponentSerializers` | Gson and legacy serializers. |
| `AudienceMessages` | Send formatted messages to players and audiences. |
| `Placeholders` | Build simple and contextual placeholders. |

```java
Component message = MiniMessages.parse(
    "<green>Hello <player>!",
    Placeholders.unparsed("player", player.getName())
);

AudienceMessages.sendMessage(player, message);

String mini = ComponentTexts.toMiniMessage(message);
String plain = ComponentTexts.toPlain(message);
```

### ⚔️ `cotani-item`

Fluent builders for modern Paper `ItemStack` creation.

| Class | Purpose |
|-------|---------|
| `ItemBuilder` | General-purpose item builder. |
| `ItemStackBuilder` | Lower-level builder around data components. |
| `ArmorBuilder` | Armor-specific helpers. |
| `SkullBuilder` | Player and texture skulls. |
| `SkullTextureResolver` | Caches texture URLs with Caffeine. |

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
    .customName("<red>Legendary Sword")
    .lore("<gray>Deals extra damage", "<dark_gray>Rare drop")
    .enchant(Enchantment.SHARPNESS, 5)
    .glow()
    .build();

ItemStack skull = SkullBuilder.create()
    .textureUrl("https://textures.minecraft.net/texture/...")
    .build();
```

### 🗄️ `cotani-storage`

Async SQL storage abstraction supporting SQLite, MySQL, and MariaDB with migrations, typed repositories, and virtual-thread-aware execution.

| Concept | Description |
|---------|-------------|
| `CotaniStorage` | Entry point for opening and managing the storage lifecycle. |
| `CotaniStorageBuilder` | Fluent builder for backends, thread pools, and repositories. |
| `CrudRepository` | Base repository with `findById`, `exists`, `save`, and `deleteById`. |
| `QueryExecutor` | Async query/update/batch/exists API backed by `CompletableFuture`. |
| `Migration` | Versioned schema migrations executed automatically at startup. |
| `StorageFuture` | `CompletableFuture` wrapper with scheduler-aware callbacks. |

```java
CotaniStorage storage = CotaniStorage.create(this)
    .sqlite("database.db")
    .virtualThreads()
    .repositories(UserRepository.class)
    .build()
    .start();

UserRepository users = storage.repository(UserRepository.class);
users.findById(player.getUniqueId())
    .thenAsync(user -> player.sendMessage("Found: " + user.name()));
```

#### Configuration example

```yaml
storage:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
    use-ssl: true
```

#### Migrations

```java
public final class CreateUsersTableMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create users table";
    }

    @Override
    public StorageFuture<Void> migrate(Schema schema) {
        return schema.table("users")
            .id("id", ColumnType.UUID)
            .required("name", ColumnType.STRING)
            .required("balance", ColumnType.DOUBLE)
            .createIfNotExists();
    }
}
```

#### Security notes

- SQL identifiers (table and column names) are validated against `^[A-Za-z_]\w*$` to prevent injection through the fluent API.
- Values are always bound via `PreparedStatement`; never concatenate user values into SQL.
- SQLite database files are confined to the plugin data folder to avoid path traversal.
- MySQL/MariaDB connections default to TLS (`useSSL=true`); disable only on trusted local networks.
- Raw SQL is intentionally omitted from exception messages to avoid leaking query contents into logs.

### 🌀 `cotani-teleport`

A thread-safe, event-driven teleport API that keeps the main thread happy.

| Concept | Description |
|---------|-------------|
| `TeleportService` | Async teleport with validation, events, and timeout. |
| `PendingTeleportService` | Delayed teleports cancellable by movement, damage, or quit. |
| `TeleportOptions` | Fine-grained control over safety, policies, player effects, and feedback. |
| `TeleportPolicyChain` | Pluggable policies for combat, cooldown, permission, and region checks. |
| `SafeLocationResolver` | Async safe-location search that avoids chunk loads. |
| `TeleportEventBus` | Dispatches Bukkit events on the main thread automatically. |

```java
TeleportModule module = TeleportModule.create(this);

module.teleportService().teleport(
    TeleportRequest.builder()
        .player(player)
        .target(target)
        .cause(TeleportCause.HOME)
        .source("/home")
        .options(TeleportOptions.builder()
            .safeLocation(true)
            .checkCooldown(true)
            .cooldownDuration(Duration.ofSeconds(30))
            .preserveVelocity(false)
            .dismount(true)
            .closeInventory(true)
            .build())
        .build()
).whenComplete((result, error) -> {
    switch (result) {
        case TeleportResult.Success success ->
            player.sendMessage("Teleported in " + success.durationMillis() + " ms");
        case TeleportResult.Failure failure ->
            player.sendMessage("Teleport failed: " + failure.reason());
    }
});

// Delayed teleport with automatic cancellation on move/damage/quit
UUID pendingId = module.pendingTeleportService().schedule(
    player, target, Duration.ofSeconds(3), TeleportOptions.defaults(), TeleportCause.WARP, "warp");
```

### ⏱️ `cotani-task`

Powerful abstractions for async work, scheduling, retries, throttling, and virtual threads.

| Concept | Description |
|---------|-------------|
| `TaskChain` | Lazy, composable async chain that only runs on terminal calls. |
| `TaskScope` | Shortcut to start chains without repeating plugin/executor. |
| `TaskResult` | Monadic success/failure carrier across async steps. |
| `VirtualThreadExecutor` | Executor backed by virtual threads for blocking I/O. |
| `PaperAsyncExecutor` | Lightweight async scheduler executor. |
| `ThrottledExecutor` | Atomic interval-based throttle. |
| `RetryPolicy` | Configurable backoff, jitter, and retry predicates. |
| `RepeatingTask` | Periodic task wired to the Paper global scheduler. |
| `CancellableTask` | Handle to cancel a running chain. |

```java
TaskScope tasks = TaskScope.of(plugin, VirtualThreadExecutor.named("my-plugin-io"));

tasks.supply(() -> database.load(player.getUniqueId()))
    .map("toSnapshot", tasks.executor(), UserData::toSnapshot)
    .mapOnMain(snapshot -> player.sendMessage("Loaded: " + snapshot.name()))
    .peek(tasks.executor(), snapshot -> metrics.record(snapshot.id()))
    .onError(tasks.executor(), error -> plugin.getLogger().severe(error.getMessage()));
```

#### 🔁 Real retry with backoff

```java
tasks.supply(() -> api.call())
    .retry(RetryPolicy.exponential(5, Duration.ofSeconds(1))
        .withJitter(0.2)
        .withSymmetricJitter()
        .retryIf(error -> error instanceof IOException))
    .join();
```

#### ⏰ Repeating task

```java
RepeatingTask task = tasks.repeat(20L, this::tick);
cotani.register(task); // cancelled automatically on disable
```

#### ⏳ Timeout

```java
tasks.supply(() -> api.call())
    .timeoutTicks(100L) // 100 ticks ≈ 5 seconds
    .join();
```

#### 🧺 Batch processing

```java
TaskChain<String> a = TaskChain.supplySync(plugin, () -> "a");
TaskChain<String> b = TaskChain.supplySync(plugin, () -> "b");

TaskResult<List<TaskResult<String>>> result = TaskChain.allOf(plugin, a, b).join();
```

#### 🛑 Cancellation

```java
CancellableTask<Data> task = tasks.supply(() -> longRunning()).asCancellable();

task.cancel();
boolean cancelled = task.isCancelled();
```

---

## 📥 Installation

### 1. Publish locally

```bash
./gradlew publishToMavenLocal
```

### 2. Add to your plugin

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.cotani:cotani-core:1.0.0")
    implementation("com.cotani:cotani-text:1.0.0")
    implementation("com.cotani:cotani-item:1.0.0")
    implementation("com.cotani:cotani-storage:1.0.0")
    implementation("com.cotani:cotani-task:1.0.0")
    implementation("com.cotani:cotani-teleport:1.0.0")
}
```

You can omit any module you do not need.

---

## 🛠️ Lifecycle in Practice

Register every `AutoCloseable` resource with `Cotani` and they will be closed in reverse registration order during `onDisable()`.

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;

    @Override
    public void onEnable() {
        VirtualThreadExecutor io = VirtualThreadExecutor.named("my-plugin-io");
        PaperAsyncExecutor async = new PaperAsyncExecutor(this);

        cotani = Cotani.forPlugin(this)
            .with(io)
            .with(async)
            .build();

        cotani.register(RepeatingTask.start(this, 20L, this::tick));
        cotani.register(new DatabaseConnection(io));
    }

    @Override
    public void onDisable() {
        cotani.close();
    }
}
```

---

## 🧪 Build & Test

Format, compile, test, and package everything:

```bash
./gradlew spotlessApply build
```

Run only tests:

```bash
./gradlew test
```

Run a specific test class:

```bash
./gradlew test --tests com.cotani.item.SkullTextureResolverTest
```

---

## 🧹 Code Quality

This project is intentionally strict:

- ✅ **Palantir Java Format** enforced via Spotless.
- ✅ **Error Prone** static analysis on every compilation.
- ✅ **NullAway** null-safety checking for `com.cotani` packages.
- ✅ **All warnings treated as errors** (`-Werror`).
- ✅ **JUnit 5** and **Mockito** for unit tests.

---

## 📋 Conventions

- Packages under `com.cotani` are annotated with `@NullMarked`.
- Fluent builders return `this` or a typed `self()`.
- Early returns are preferred over nested conditionals.
- `Objects.requireNonNull` guards every public parameter.
- Records are preferred for immutable data carriers.
- String concatenation inside hot loops is avoided.

---

## 🤝 Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/my-feature`.
3. Make your changes and add tests for new public APIs.
4. Run `./gradlew spotlessApply build` and ensure everything passes.
5. Open a pull request with a clear description.

---

## 📄 License

Cotani is released under the MIT License. See [LICENSE](LICENSE) for details.

---

<p align="center">
  Built with ☕ Java 25, ⚔️ Paper, and ❤️ for clean code.
</p>

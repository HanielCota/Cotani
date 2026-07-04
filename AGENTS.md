# AGENTS.md

## Project Context

This is a Java project focused on clean architecture, modular APIs, async-safe execution and Paper/PaperSpigot plugin
development.

Prioritize:

1. correctness;
2. main-thread safety;
3. non-blocking async execution;
4. clear API contracts;
5. null-safety;
6. SRP;
7. testability;
8. maintainability.

Prefer small, safe changes over broad rewrites.

## Skills

Use the repository skills intentionally:

- `java-engineering-standards`: general Java quality, null-safety, naming, SRP, records, `var`, organization.
- `java-async-standards`: `CompletionStage`, `CompletableFuture`, executors, schedulers, cache async, database async,
  timeout, retry and sync/async boundaries.
- `paper-plugin-architecture`: Paper/PaperSpigot lifecycle, listeners, commands, services, repositories, cache, config,
  messages and Bukkit/Paper main-thread safety.
- `java-api-standards`: public APIs, module contracts, factories, builders, value objects, result types, exceptions,
  `Optional`, `CompletionStage` and API/impl separation.

When a task touches async code, always apply `java-async-standards`.

When a task touches Bukkit/Paper API, always apply `paper-plugin-architecture`.

When a task changes public contracts, always apply `java-api-standards`.

## Hard Rules

Do not use blocking calls in application code:

```java
future.join();
future.get();
Thread.sleep(...);
TimeUnit.SECONDS.sleep(...);
TimeUnit.MILLISECONDS.sleep(...);
```

Do not hide blocking behind helpers such as:

```java
FutureUtils.await(...)
AsyncUtils.sleep(...)
blockingGet(...)
waitFor(...)
```

Do not access Bukkit/Paper APIs asynchronously unless the API is explicitly documented as thread-safe.

Do not capture live Bukkit/Paper objects into async flows:

```java
Player
World
Entity
Inventory
Block
```

Capture immutable identifiers instead, such as `UUID`, then return to the main thread before touching Bukkit/Paper APIs.

Do not use implicit async executors:

```java
CompletableFuture.supplyAsync(() -> ...)
CompletableFuture.runAsync(() -> ...)
```

Always pass an explicit executor or scheduler abstraction.

Do not use `JavaPlugin` as a global service locator.

Avoid static mutable state.

Do not expose mutable internal collections.

Do not return `null` from public APIs.

## Java Style

Assume Java 17+ unless the module says otherwise.

Use:

- constructor injection;
- `Objects.requireNonNull` for required dependencies and public inputs;
- `final class` for concrete implementations;
- `record` for immutable data carriers and value objects;
- `CompletionStage<T>` for public async APIs;
- `Optional<T>` only for expected absence in return values;
- immutable collection copies with `List.copyOf`, `Set.copyOf`, `Map.copyOf`.

Use `var` only when the type is obvious from the right-hand side or the variable name makes the meaning clear.

Avoid vague class names:

```text
Manager
Helper
Util
Data
Core
System
```

Prefer domain names:

```text
UserService
UserRepository
UserCache
TaskScheduler
MessageService
StorageProvider
```

## Paper/PaperSpigot Architecture

Keep the main plugin class thin. It should only handle lifecycle and bootstrap.

Listeners should:

1. receive events;
2. extract immutable values;
3. perform simple guards;
4. delegate to services.

Commands should:

1. validate sender;
2. parse arguments;
3. check permissions;
4. delegate to services.

Services should contain use cases and business rules.

Repositories should encapsulate persistence.

Caches should be explicit and isolated.

Config, messages and permissions should be centralized.

Resources created during startup must be closed on shutdown.

## Async Flow

Preferred Paper async flow:

```text
event on main thread
  -> capture immutable IDs
  -> run IO async
  -> process safe data async
  -> return to main thread
  -> touch Bukkit/Paper API
  -> continue async persistence/audit if needed
```

Use async composition:

```java
thenApply
thenCompose
thenCombine
exceptionallyCompose
whenComplete
```

Do not block to extract future results.

Do not use `sleep` for delay, retry or polling. Use a scheduler/delay abstraction.

External IO should have timeout where appropriate.

Retries must have a limit and should only be used for idempotent or safely repeatable operations.

## Public API Design

Public APIs should expose intent, not implementation.

Prefer:

```java
CompletionStage<Optional<User>> findUserAsync(UserId userId);
CompletionStage<User> getOrCreateUserAsync(UserId userId);
```

Avoid:

```java
UserManager getManager();
Map<String, Object> getData(UUID id);
Object execute(Object input);
```

Use value objects for important domain concepts.

Use result types for expected domain outcomes.

Use domain exceptions for invalid states or unexpected failures.

Keep implementation classes in `impl` or `internal` packages when possible.

## Validation Commands

Before completing code changes, run the relevant validation command if available.

Common commands:

```bash
./gradlew test
./gradlew check
./gradlew build
```

If a command is unavailable, fails because of environment setup, or is too broad for the change, explain what was run
and what could not be verified.

## Refactoring Policy

When refactoring:

1. preserve behavior;
2. avoid public API changes unless necessary;
3. make small cohesive changes;
4. explain compatibility impact if contracts change;
5. add or suggest tests for changed behavior.

Do not rewrite large areas without a clear reason.

Do not introduce dependencies unless justified.

Do not change formatting-only across unrelated files.

## Using Cotani Modules

When generating or modifying code that consumes Cotani APIs, follow these module-specific patterns.

### General rules

1. Prefer `CompletionStage` over `CompletableFuture` in public APIs; use `CompletableFuture` only when the upstream API requires it.
2. Capture immutable identifiers (`UUID`, value objects) before leaving the main thread.
3. Return to the main thread through `PaperTaskScheduler` or `TaskChain` before touching Bukkit/Paper objects.
4. Never expose mutable internal collections; return `List.copyOf`, `Set.copyOf` or `Map.copyOf`.
5. Never return `null` from public methods; use `Optional` for expected absence.
6. Use constructor injection; do not use `JavaPlugin` as a service locator.
7. Close resources created during startup via `Cotani.forPlugin(...)`.

### cotani-task

- Create the scheduler once in the plugin `onEnable`: `PaperTaskScheduler scheduler = SchedulerFactory.create(plugin);`.
- Use `TaskChain` for async→global/region/entity transitions instead of manual `Bukkit.getScheduler()` calls.
- Apply `timeout(...)` and `retry(...)` only to idempotent operations.
- Use named tasks (`async("name", ...)`) to improve metrics and debugging.
- Do not call `future.join()` or `future.get()` to extract results.

### cotani-cache

- Implement `CacheRepository<K, V>` for persistence; never access Bukkit/Paper APIs from its methods.
- Use `updateAsync` for immutable values and `mutateAsync` for mutable values.
- Always call `cache.close()` on shutdown; dirty entries are saved automatically.
- Prefer `PlayerDataCache` for player-bound data; use `DataCache` for generic key-value pairs.
- Avoid `cache.get()` on a key that is not loaded; use `getOrLoadAsync` or `find`.

### cotani-config

- Represent configuration as `record`s with `@Default`, `@Required`, `@Range` and `@ConfigPath`.
- Validate on load and report issues instead of silently falling back.
- Use `reloadAsync()` and rebind configs inside the completion stage.
- Do not mutate bound config records; reload and rebind when values change.

### cotani-storage

- Start storage asynchronously: `storage.startAsync().thenAccept(...)`. Do not call `.join()`.
- Keep repositories in the `repository` package and extend `CotaniRepository`.
- Use `TransactionManager` for operations that touch multiple rows/tables.
- Never call `QueryExecutor` or repository methods from the main thread directly; compose through `CompletionStage`.
- Register migrations in creation order; do not skip versions.

### cotani-user

- Resolve users through `UserService` (`findAsync`, `getOrThrowAsync`) instead of calling `Bukkit.getOfflinePlayer` in async flows.
- Do not store live `Player` references in services; store `UUID` and re-resolve on the main thread when needed.

### cotani-economy

- Always pass an `EconomyOperationId` for idempotency; generate it before calling the service.
- Use `EconomyReason` to tag the cause of transactions.
- Handle domain exceptions (`InsufficientFundsException`, `InvalidAmountException`, etc.) in the async pipeline.
- Do not manipulate balances directly; route every change through `EconomyService`.

### cotani-teleport

- Use `TeleportRequest.builder()` and explicit `TeleportOptions` for every teleport.
- Provide real `CombatAdapter` and `RegionProtectionAdapter` integrations; do not rely on the noop defaults in production.
- Handle both `TeleportResult.Success` and `TeleportResult.Failure` in the completion stage.
- Prefer `CotaniTeleports.create(...)` over the deprecated static `CotaniTeleport` facade.

## Anti-patterns by module

| Module | Do not | Do instead |
|--------|--------|------------|
| task | `future.join()` / `future.get()` | compose with `thenApply` / `thenCompose` |
| task | `Bukkit.getScheduler().runTask(...)` manually | use `scheduler.global(...)` or `TaskChain` |
| cache | `cache.get(player)` before load | `getOrLoadAsync(...)` |
| cache | manual dirty checks | rely on `markDirty` / autosave |
| config | mutable config classes | immutable `record`s |
| config | sync file reload on main thread | `reloadAsync()` |
| storage | call `.start()` synchronously | `startAsync()` |
| storage | raw `Statement`/`ResultSet` in services | use `TableQuery` / repositories |
| user | store `Player` in async flows | store `UUID`, resolve on main thread |
| economy | reuse the same `EconomyOperationId` for different operations | one unique id per logical operation |
| teleport | call `player.teleport(...)` directly | use `TeleportService.teleport(...)` |
| teleport | ignore `TeleportResult.Failure` | handle failure reason and notify player |

## Agent Cookbook

See [`docs/ai/cotani-cookbook.md`](docs/ai/cotani-cookbook.md) for copy-paste recipes covering common plugin scenarios (player data cache, economy command, teleport command, config reload, etc.).

## Review Output

When reviewing code, structure the response as:

1. problems found;
2. why they matter;
3. suggested design;
4. refactored code or patch;
5. tests to add/update;
6. remaining risks.

For small tasks, keep the response concise.

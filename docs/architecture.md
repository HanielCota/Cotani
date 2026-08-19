# Cotani architecture

Cotani separates lifecycle, execution, infrastructure and gameplay concerns into independently consumable modules. A plugin declares only its top-level modules; Gradle resolves their transitive Cotani dependencies.

## Module dependency graph

An arrow from `A` to `B` means that module `A` directly depends on module `B`. The BOM is not shown because it aligns versions and adds no runtime dependency.

```mermaid
flowchart LR
    core["core"]
    task["task"]
    text["text"]
    item["item"]
    config["config"]
    storage["storage"]
    cache["cache"]
    user["user"]
    economy["economy"]
    cooldown["cooldown"]
    teleport["teleport"]
    event["event"]
    gui["gui"]
    display["display"]
    metrics["metrics"]

    task --> core
    text --> core
    item --> core
    item --> text
    config --> core
    config --> task
    config --> text
    storage --> task
    storage --> text
    cache --> core
    cache --> task
    cache --> storage
    cache --> config
    user --> core
    user --> task
    user --> text
    user --> storage
    economy --> core
    economy --> task
    economy --> text
    economy --> config
    economy --> storage
    cooldown --> core
    cooldown --> task
    cooldown --> config
    cooldown --> storage
    cooldown --> cache
    teleport --> core
    teleport --> task
    teleport --> text
    teleport --> config
    teleport --> cooldown
    event --> core
    gui --> text
    gui --> item
    display --> core
    display --> task
    display --> text
    display --> item
    metrics --> task
    metrics --> config
    metrics --> storage
    metrics --> cache
```

## Responsibility layers

| Layer | Modules | Responsibility |
| --- | --- | --- |
| Lifecycle | `core` | Own and close resources without acting as a service locator |
| Execution and presentation | `task`, `text`, `item` | Thread transitions, messages and item construction |
| Infrastructure | `config`, `storage`, `cache` | Configuration, persistence and state coordination |
| Domain features | `user`, `economy`, `cooldown`, `teleport`, `event`, `gui`, `display` | Reusable plugin use cases and user-facing behavior |
| Operations | `metrics` | Runtime measurements and optional Prometheus export |

## Runtime execution boundary

Paper and Folia objects stay on the thread that owns them. Asynchronous flows carry immutable identifiers and plain data across the boundary.

```mermaid
sequenceDiagram
    actor Server as Paper / Folia owner thread
    participant Scheduler as cotani-task
    participant Service as Cotani service
    participant IO as storage / cache

    Server->>Scheduler: UUID and immutable input
    Scheduler->>Service: run on explicit async executor
    Service->>IO: compose non-blocking operation
    IO-->>Service: immutable result
    Service-->>Scheduler: CompletionStage result
    Scheduler-->>Server: global / region / entity transition
    Server->>Server: access Bukkit / Paper objects
```

Never carry live `Player`, `World`, `Entity`, `Inventory` or `Block` instances into the asynchronous portion of this flow. See [asynchronous API contracts](async-contracts.md) for the complete execution and failure semantics.

# Cotani architecture

Cotani separates lifecycle, execution, infrastructure and gameplay concerns into independently consumable modules. A plugin declares only its top-level modules; Gradle resolves their transitive Cotani dependencies.

## Module dependency graph

An arrow from `A` to `B` means that module `A` directly depends on module `B`. The BOM is not shown because it aligns versions and adds no runtime dependency.

```mermaid
flowchart LR
    core["core"]
    audit["audit"]
    auditStorage["audit-storage"]
    task["task"]
    text["text"]
    locale["locale"]
    item["item"]
    config["config"]
    storage["storage"]
    cache["cache"]
    redis["redis"]
    user["user"]
    economy["economy"]
    cooldown["cooldown"]
    teleport["teleport"]
    event["event"]
    gui["gui"]
    display["display"]
    command["command"]
    hud["hud"]
    nametag["nametag"]
    npc["npc"]
    region["region"]
    dialog["dialog"]
    metrics["metrics"]
    punishment["punishment"]
    location["location"]
    mail["mail"]
    inventory["inventory"]
    permission["permission"]
    placeholder["placeholder"]
    reward["reward"]
    rewardIntegration["reward-integration"]
    friend["friend"]
    queue["queue"]
    trade["trade"]

    task --> core
    audit --> core
    auditStorage --> audit
    auditStorage --> storage
    text --> core
    locale --> core
    locale --> text
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
    redis --> core
    redis --> task
    redis --> config
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
    command --> core
    command --> task
    command --> text
    hud --> core
    hud --> task
    hud --> text
    hud --> gui
    nametag --> core
    nametag --> task
    nametag --> text
    npc --> core
    npc --> task
    npc --> text
    region --> core
    region --> task
    region --> text
    dialog --> core
    dialog --> task
    dialog --> text
    metrics --> task
    metrics --> config
    metrics --> storage
    metrics --> cache
    punishment --> core
    punishment --> audit
    punishment --> storage
    location --> core
    location --> task
    location --> teleport
    location --> storage
    mail --> core
    mail --> storage
    reward --> core
    reward --> storage
    rewardIntegration --> reward
    rewardIntegration --> economy
    rewardIntegration --> inventory
    rewardIntegration --> task
    inventory --> core
    inventory --> task
    inventory --> storage
    permission --> core
    permission --> storage
    placeholder --> core
    placeholder --> task
    placeholder --> text
    friend --> core
    friend --> event
    queue --> core
    queue --> event
    trade --> core
    trade --> event
    trade --> economy
```

## Responsibility layers

| Layer | Modules | Responsibility |
| --- | --- | --- |
| Lifecycle | `core` | Own and close resources without acting as a service locator |
| Execution and presentation | `task`, `text`, `item`, `locale` | Thread transitions, messages, localized catalogs and item construction |
| Infrastructure | `config`, `storage`, `cache`, `redis` | Configuration, persistence, caching, distributed synchronization, and admission-controlled external I/O |
| Domain features | `user`, `economy`, `cooldown`, `teleport`, `event`, `gui`, `display`, `command`, `hud`, `nametag`, `npc`, `region`, `dialog`, `permission`, `placeholder`, `inventory`, `friend`, `queue`, `trade`, `punishment`, `location`, `mail`, `reward` | Reusable player and gameplay use cases, permission decisions, placeholder expansion, inventory synchronization, friendships, matchmaking queues, confirmation-based trading, moderation punishments, saved homes and warps, persistent player mail, idempotent rewards, NPCs, 3D regions, HUD, nametags, and reactive interfaces |
| Integration | `reward-integration` | Standard settlement adapters that deliver reward currency and items |
| Operations | `audit`, `audit-storage`, `metrics` | Immutable audit history, SQL audit persistence, runtime measurements, and optional Prometheus export |

## Runtime execution boundary

Paper and Folia objects stay on the thread that owns them. Asynchronous flows carry immutable identifiers and plain data across the boundary.

```mermaid
sequenceDiagram
    actor Server as Paper / Folia owner thread
    participant Scheduler as cotani-task
    participant Service as Cotani service
    participant IO as storage / cache / redis

    Server->>Scheduler: UUID and immutable input
    Scheduler->>Service: run on explicit async executor
    Service->>IO: compose non-blocking operation
    IO-->>Service: immutable result
    Service-->>Scheduler: CompletionStage result
    Scheduler-->>Server: global / region / entity transition
    Server->>Server: access Bukkit / Paper objects
```

Never carry live `Player`, `World`, `Entity`, `Inventory` or `Block` instances into the asynchronous portion of this flow. See [asynchronous API contracts](async-contracts.md) for the complete execution and failure semantics.

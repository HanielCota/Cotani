# Architecture Diagram: Why It's Fast

This diagram explains the performance scaling mechanism of the Tablist renderer. It ensures computational cost scales directly with state **changes**, rather than the player count or server tick rate.

## Mermaid Flowchart

```mermaid
flowchart LR
    %% Visual configuration for nodes (Neon Themes)
    classDef default fill:#0d1527,stroke:#6366f1,stroke-width:1.5px,color:#f3f4f6;
    classDef event fill:#0d1527,stroke:#3b82f6,stroke-width:1.5px,color:#f3f4f6;
    classDef dirty fill:#0d1527,stroke:#ef4444,stroke-width:1.5px,color:#f3f4f6;
    classDef build fill:#0d1527,stroke:#a855f7,stroke-width:1.5px,color:#f3f4f6;
    classDef cache fill:#0d1527,stroke:#eab308,stroke-width:1.5px,color:#f3f4f6;
    classDef diff fill:#0d1527,stroke:#14b8a6,stroke-width:1.5px,color:#f3f4f6;
    classDef skip fill:#0d1527,stroke:#6b7280,stroke-width:1.5px,color:#f3f4f6;
    classDef render fill:#0d1527,stroke:#22c55e,stroke-width:1.5px,color:#f3f4f6;

    %% Nodes Definition
    Event["<b>Event</b><br>(join - quit - frame -<br>reload - group change)"]:::event
    Dirty["<b>🚩 Dirty set</b><br>(deduplicating)"]:::dirty
    Build["<b>Build TabSnapshot</b>"]:::build
    Cache[("<b>⚡ Caffeine</b><br>async cache")]:::cache
    Diff{"<b>Diff vs<br>last sent</b>"}:::diff
    Skip["<b>Skip</b><br>sending"]:::skip
    Render["<b>Render</b><br>rows + headers"]:::render

    %% Connections & Labels
    Event -->|marks viewer| Dirty
    Dirty -->|flush loop drains<br>once per tick| Build
    Build -->|placeholders / MiniMessage| Cache
    Cache -.->|cache hit| Build
    Build --> Diff
    Diff -->|empty diff| Skip
    Diff -->|delta only| Render
```

## How It Works (The 3 Core Mechanisms)

1. **Deduplication (Dirty Set)**: State changes queue dirty entities, collapse redundant triggers occurring within the same tick, and delay calculations to a single flush cycle per tick.
2. **Asynchronous Cache (Caffeine)**: String parsing and template compilations (MiniMessage, Placeholders) run off the server's main thread and are cached inside Caffeine.
3. **Delta Rendering (Diff Checking)**: A comparison check between the compiled frame and the client's last active frame calculates the exact structural difference. If there are no updates, the packet is skipped. Otherwise, only changed rows are transmitted.

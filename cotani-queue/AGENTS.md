# cotani-queue module instructions

## Scope

This module owns immutable player queue entries and atomic matchmaking groups. It accepts UUIDs and plain immutable
values only; it does not retain Bukkit/Paper objects or register commands/listeners.

## Rules

1. Use `CompletionStage` for persistence and event-bus operations.
2. Use `QueueRepository.saveAsync(snapshot, expectedRevision)` for optimistic persistence.
3. Persist before replacing visible queue state.
4. A player may have only one active ticket across all queues in one service.
5. Matching removes the selected tickets atomically and returns an immutable `QueueMatch`.
6. Expired tickets are ignored by reads and removed by the next mutation.
7. Event publication is best effort, bounded by `QueueServiceOptions.eventTimeout()` and logged on failure.
8. Close through `closeAsync()` and compose its completion into plugin shutdown.

## Validation

```bash
./gradlew :queue:spotlessApply :queue:check
./gradlew check integrationTest
```

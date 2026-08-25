# cotani-friend module instructions

## Scope

This module owns immutable friendships, friend requests and player blocks. It accepts only UUIDs and plain immutable
values; it does not retain Bukkit/Paper objects or register commands/listeners.

## Rules

1. Use `CompletionStage` for all operations that can touch persistence or the event bus.
2. Use `FriendRepository.saveAsync(snapshot, expectedRevision)` for optimistic revision checks.
3. Persist a new snapshot before replacing the visible in-memory snapshot.
4. Keep reads non-blocking and return immutable collections.
5. Do not create friendships when either player blocks the other.
6. Event publication is best effort, bounded by `FriendServiceOptions.eventTimeout()` and logged on failure.
7. Close through `closeAsync()` and compose its completion into plugin shutdown.

## Validation

```bash
./gradlew :friend:spotlessApply :friend:check
./gradlew check integrationTest
```

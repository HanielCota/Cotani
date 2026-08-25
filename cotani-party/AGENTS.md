# cotani-party module instructions

## Scope

This module owns immutable party aggregates, invitations, role changes and asynchronous persistence contracts. It does not
retain Bukkit/Paper objects or register commands/listeners.

## Rules

1. Keep actors and members as immutable `UUID` values.
2. Use `PartyOptions` to enforce a bounded party size.
3. Use explicit positive invitation durations and remove expired invitations before returning them.
4. Persist mutations before replacing the visible in-memory aggregate.
5. Use `createAsync`, `updateAsync` with the previous revision and `deleteAsync` with the previous revision.
6. Keep repository calls asynchronous, serialized and bounded by `PartyServiceOptions.repositoryTimeout()`.
7. Publish only immutable `PartyEvent` values; event publication must not expose mutable service state.
8. Treat event delivery as best effort and observe failures through the service logger.
9. Invitations are ephemeral and are not part of `PartySnapshot`; use a separate durable invitation module when needed.
10. Use `transferLeadershipAsync` before removing a leader; the service selects the oldest successor when the leader leaves.
11. Close through `closeAsync()` and compose the stage into plugin shutdown.

## Validation

```bash
./gradlew :party:spotlessApply :party:check
./gradlew check integrationTest
```

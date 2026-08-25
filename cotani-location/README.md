# cotani-location

Asynchronous homes and warps for Paper and Folia plugins.

The module keeps saved coordinates as immutable data, exposes a repository SPI for persistence, and resolves live
`World`/`Location` objects only on the server-owned thread immediately before delegating to `cotani-teleport`.

## Core API

```java
LocationService locations = CotaniLocations.inMemory();

locations.setHomeAsync(playerId, LocationName.of("base"), position);
locations.findHomeAsync(playerId, LocationName.of("base"));
locations.setWarpAsync(LocationName.of("spawn"), position);
```

For persistence, implement `LocationRepository` and restore with `CotaniLocations.fromRepositoryAsync(...)`. The
repository is updated before the in-memory state is replaced. Mutations are incremental (`saveHomeAsync`,
`deleteHomeAsync`, `saveWarpAsync`, and `deleteWarpAsync`), so unrelated rows are not rewritten on every change.

The built-in `StorageLocationRepository` uses `StorageLocationRepository.migrations()` and per-location upserts;
register those migrations before starting `CotaniStorage`.

For teleport integration, create `LocationTeleportService` with a `TeleportService` and `PaperTaskScheduler`. It
accepts only immutable player identifiers and saved positions; it never carries live Bukkit objects into async work.

## Design guarantees

- public async contracts use `CompletionStage`;
- mutations are serialized and persistence happens before visible state changes;
- home names are scoped per player and warp names are global;
- home limits are enforced by `LocationServiceOptions`;
- repository timeout failures do not release the mutation queue or shutdown before the underlying write finishes;
- closing rejects new work and waits for the last accepted mutation barrier;
- missing homes/warps and unavailable worlds are reported with domain exceptions;
- no blocking calls are used by the module.

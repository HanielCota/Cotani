# Cotani 1.x migration notes

These notes describe the compatibility additions and behavior changes carried into the `1.1.0` release. The `1.0.0`
line remains available for consumers that still target its original module set; new integrations should use the current
stable APIs below.

## Explicit async names

Cotani 1.1.0 retains source-compatible `*Async` aliases for APIs whose original names returned a `CompletionStage`
without saying so in the method name:

- `DataCache`: `getOrLoadAsync`, `loadAsync`, `updateAsync`, `mutateAsync`, `saveAsync`, `saveDirtyAsync`, `saveAllAsync`;
- `PlayerDataCache`: `saveDirtyAsync`, `saveAllAsync`;
- `EconomyService`: `balanceAsync`, `hasAsync`, `depositAsync`, `withdrawAsync`, `setAsync`, `transferAsync`;
- `TeleportService`: `teleportAsync`.

Existing 1.x method names remain available and retain their behavior. New code should use the explicit aliases. A future 2.0 release may remove the ambiguous names only after a deprecation cycle.

## Teleport artifact

`cotani-teleport` is a library, not a standalone Paper plugin. Its jar no longer contains `plugin.yml` or a bootstrap that silently selects noop combat and protection adapters. Consumers must shade the library into their plugin and create the module with real adapters:

```java
var teleports = CotaniTeleports.create(plugin, combatAdapter, regionAdapter, scheduler);
```

## Metrics namespace

Use `com.cotani.metrics.CotaniMetrics` as the new stable factory. Returned metrics types retain their `net.cotani.metrics` namespace for binary compatibility. A complete package move is reserved for 2.0 because Java cannot provide transparent aliases for every final class and record.

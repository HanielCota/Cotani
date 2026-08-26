# Cotani 1.x migration notes

These notes describe the compatibility additions and behavior changes carried into the `1.1.1` release. The `1.0.0`
line remains available for consumers that still target its original module set; new integrations should use the current
stable APIs below.

## Explicit async names

Cotani 1.1.1 retains source-compatible `*Async` aliases for APIs whose original names returned a `CompletionStage`
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

All metrics types now live under `com.cotani.metrics`: `CotaniMetricsModule`, `CotaniMetricsRegistry`, `api.*`, `binder.*`, `config.MetricsConfig` and `exporter.PrometheusServer`. The legacy `net.cotani.metrics` packages were removed; update imports and Shadow relocation rules accordingly.

## HUD controller names

The HUD presentation APIs were renamed from `*Manager` to `*Controller` to make their responsibilities explicit:
`TabListController`, `BossBarController` and `ActionBarController`. Update imports and local variable types; the
module accessors `HudModule.tabList()`, `HudModule.bossBar()` and `HudModule.actionBar()` return the controller types.

## Permission assignments

`PermissionSubjectData` was replaced by the immutable `PermissionAssignments` value object. Update service and
repository integrations to use `PermissionAssignments`, including its immutable `permissions()` and `groups()` values.

## Recovery and shutdown behavior

Reward settlement now uses a direct `(playerId, rewardId)` pending-claim lookup, so player-facing recovery is not
limited to the first 1,000 global claims. Storage and task scheduler shutdowns release owned resources even when an
executor or delayed scheduler is already unavailable. Marketplace mutations are rejected atomically once shutdown
begins, while already accepted purchase settlement remains recoverable.

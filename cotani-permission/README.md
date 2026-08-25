# cotani-permission

Asynchronous permission evaluation and persistence for Paper and Folia plugins.

The service stores immutable UUID-based subjects, named groups and explicit `ALLOW`, `DENY` or `UNSET` nodes. Group
priority and inheritance are resolved by the module; callers receive a `PermissionDecision` instead of reading mutable
maps or Bukkit state.

## In-memory usage

```java
PermissionService permissions = CotaniPermissions.inMemory(
        new PermissionGroup("moderator", 100,
                Map.of(new PermissionNode("server.moderate"), PermissionState.ALLOW)));

UUID playerId = player.getUniqueId();
permissions.assignGroupAsync(playerId, "moderator")
        .thenCompose(ignored -> permissions.checkAsync(playerId, "server.moderate"))
        .thenAccept(decision -> {
            if (decision.allowed()) {
                // Return to the owning Paper/Folia thread before touching Bukkit objects.
            }
        });
```

For a custom persistence implementation, restore the service with `CotaniPermissions.fromRepositoryAsync(repository)`.
The SQL adapter is created with `CotaniPermissions.storageAsync(storage)`; register
`CotaniPermissions.migrations()` before starting `CotaniStorage`.

All mutations return `CompletionStage`, are serialized by the service and persist before the new state becomes visible.
Close the service with `closeAsync()` during plugin shutdown.

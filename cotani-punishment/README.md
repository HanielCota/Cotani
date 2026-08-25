<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-punishment

</div>

Immutable asynchronous moderation records for bans, mutes and warnings.

The API supports stable idempotency keys, temporary or permanent punishments, bounded history queries, active-state
evaluation at an explicit instant, and revocation with actor, reason and timestamp. It stores UUIDs and immutable
values only, so commands and listeners remain responsible for Paper/Folia thread transitions.

```java
CompletionStage<PunishmentService> serviceStage =
        CotaniPunishments.storageAsync(storage, auditService);

serviceStage.thenAccept(punishments -> {
    Cotani.forPlugin(plugin).withAsync(punishments::closeAsync).build();
    punishments.applyAsync(request).thenAccept(punishment -> {
        // Notify the player on its owning server/entity thread.
    });
});
```

Register `CotaniPunishments.migrations()` before starting `CotaniStorage`. The service is created after storage is
running and its asynchronous close is owned by the plugin lifecycle.

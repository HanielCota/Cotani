# cotani-punishment

Immutable asynchronous moderation records for bans, mutes and warnings.

The API supports stable idempotency keys, temporary or permanent punishments, bounded history queries, active-state
evaluation at an explicit instant, and revocation with actor, reason and timestamp. It stores UUIDs and immutable
values only, so commands and listeners remain responsible for Paper/Folia thread transitions.

```java
CompletionStage<PunishmentService> serviceStage =
        CotaniPunishments.storageAsync(storage, auditService);

serviceStage.thenCompose(punishments -> punishments.applyAsync(request))
        .thenAccept(punishment -> {
            // Notify the player on its owning server/entity thread.
        });
```

Register `CotaniPunishments.migrations()` before starting `CotaniStorage`. Register the service with
`Cotani.forPlugin(plugin).withAsync(punishments::closeAsync)`.


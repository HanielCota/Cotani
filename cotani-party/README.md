<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-party

</div>

Asynchronous party groups with immutable members, expiring invitations, roles, leadership transfer and persistence.

The service stores UUIDs only. Party mutations are serialized, guarded by aggregate revisions and persisted before
the new snapshot is published. Invitations are intentionally in memory and expire; `PartySnapshot` persists party
aggregates only.

```java
PartyService parties = CotaniParties.inMemory();

parties.createAsync(leaderId, PartyOptions.defaults())
        .thenCompose(party -> parties.inviteAsync(
                party.id(), leaderId, invitedPlayerId, Duration.ofMinutes(2)))
        .thenCompose(invite -> parties.acceptInviteAsync(invitedPlayerId, invite.partyId()));
```

Use `CotaniParties.fromRepositoryAsync(repository, eventBus, options)` for persistence. Repository and event
timeouts only bound the caller-facing stage; accepted underlying work remains serialized. Close with `closeAsync()`.

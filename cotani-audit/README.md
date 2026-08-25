# cotani-audit

Append-only audit records with immutable values, bounded queries and ordered asynchronous persistence.

```java
AuditService audit = CotaniAudits.inMemory();

audit.recordAsync(AuditEntry.now(
        AuditActor.player(playerId),
        AuditAction.of("permission.group.assign"),
        AuditTarget.resource("player", playerId.toString()),
        AuditSeverity.INFO,
        Map.of("group", "moderator")))
    .thenCompose(ignored -> audit.findAsync(AuditQuery.builder().limit(100).build()));
```

Use `CotaniAudits.fromRepository(repository)` for a custom `AuditRepository`. The service accepts UUIDs and plain
values only; it never retains `Player`, `World` or other Bukkit objects. Writes are serialized in submission order,
queries wait for earlier successful writes, and new work is rejected after `closeAsync()` begins.

For SQL persistence, use the separate [`cotani-audit-storage`](../cotani-audit-storage/README.md) adapter.


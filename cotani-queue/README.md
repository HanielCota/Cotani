<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-queue

</div>

Asynchronous priority queues and atomic matchmaking for Paper and Folia plugins.

Tickets contain immutable UUIDs, priority and expiration. Active entries are ordered by priority descending and
sequence ascending, while `matchAsync` removes a complete match atomically. Persistence uses optimistic snapshot
revisions and optional `cotani-event` publication.

```java
QueueService queues = CotaniQueues.inMemory();

queues.enqueueAsync(QueueId.of("duel"), playerId, QueueEntryOptions.defaults())
        .thenCompose(ticket -> queues.matchAsync(ticket.queueId(), 2))
        .thenAccept(match -> match.ifPresent(this::startMatch));
```

Use `CotaniQueues.fromRepositoryAsync(repository, eventBus, options)` for recovery. Operations accepted before
`closeAsync()` are serialized; event publication is best effort and does not undo committed queue state.

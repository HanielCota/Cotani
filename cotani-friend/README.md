<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-friend

</div>

Asynchronous friendships, requests and directional player blocks for Paper and Folia plugins.

The module uses immutable UUID-based contracts, optimistic snapshot revisions and optional `cotani-event` domain
events. Mutations are persisted before becoming visible; event delivery is best effort and does not roll back a
committed friendship.

```java
FriendService friends = CotaniFriends.inMemory();

friends.sendRequestAsync(requesterId, targetId)
        .thenCompose(request -> friends.acceptRequestAsync(
                request.targetId(), request.requesterId()))
        .thenCompose(ignored -> friends.friendsAsync(requesterId));
```

Use `CotaniFriends.fromRepositoryAsync(repository, eventBus, options)` to restore a `FriendSnapshot`. Close the
service with `closeAsync()`; accepted operations finish before shutdown completes.

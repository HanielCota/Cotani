<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-quest

</div>

Objective-based player quests with asynchronous progress, optimistic persistence, idempotent claims and domain events.

```java
var quests = CotaniQuests.storage(storage, eventBus);
quests.register(new QuestDefinition(
        QuestId.of("first-mining"),
        List.of(new QuestObjective(
                QuestObjectiveId.of("mine-diamond"), "mine", "diamond_ore", 3)),
        RewardId.of("daily-mining")));

quests.recordProgressAsync(playerId, QuestId.of("first-mining"), QuestObjectiveId.of("mine-diamond"), 1);
```

When a quest is complete, claim it with a stable `QuestClaimId` and use
`claim.rewardClaimId()` as the idempotency key when handing the reward to `cotani-reward`:

```java
var claimId = QuestClaimId.random();
quests.claimAsync(playerId, questId, claimId)
        .thenCompose(claim -> rewards.claimAsync(playerId, claim.rewardId(), claim.rewardClaimId()));
```

Register [`CotaniQuests.migrations()`](src/main/java/com/cotani/quest/CotaniQuests.java) before starting storage.
The service never accesses Bukkit objects; listeners should capture the player's UUID and objective values first.
Mutations are serialized per player and quest, bounded by `QuestServiceOptions.maxPendingMutations()` and completed only
after persistence. Event delivery is best effort and bounded by the configured event timeout.

<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-reward

</div>

Persistent, idempotent reward claims for Paper and Folia plugins.

`cotani-reward` decides whether a player may claim a reward: cooldowns, streak windows, total counts and the claim
idempotency key are evaluated by the repository. It does not touch Bukkit, inventories or economy APIs. Settlement is
provided by [`cotani-reward-integration`](../cotani-reward-integration/README.md) or a custom handler.

```java
RewardService rewards = CotaniRewards.inMemory();
rewards.register(dailyDefinition);

RewardSettlementService settlement = CotaniRewards.settlement(rewards, handlers);
settlement.claimOrRecoverAsync(playerId, RewardId.of("daily"))
        .thenAccept(claim -> {
            // Every grant is durable before the claim is acknowledged.
        });
```

For SQL, create a `StorageRewardRepository` over started storage. Keep the same `RewardClaimId` across retries;
recover unfinished deliveries with `pendingClaimsAsync(limit)` and acknowledge them through `markSettledAsync(...)`.
Compose `closeAsync()` into plugin shutdown.

# cotani-reward-integration

Settlement adapters connecting `cotani-reward` to `cotani-economy` and `cotani-inventory`.

```java
var settlement = CotaniRewards.settlement(rewards, List.of(
        CotaniRewardIntegrations.economy(economyService),
        CotaniRewardIntegrations.vanillaInventory(plugin, inventories.service())));

settlement.claimAndSettleAsync(playerId, RewardId.of("daily"));
```

Currency operations derive deterministic `EconomyOperationId` values from the claim and grant index. Inventory grants
are applied on the player's entity thread and receive persistent delivery markers. If marked items can be consumed or
moved before recovery, provide a custom durable item resolver/handler for stronger crash guarantees.


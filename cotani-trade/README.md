<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-trade

</div>

Asynchronous two-player trading with immutable offers, confirmations, expiration, optimistic persistence and an
idempotent settlement SPI.

```java
TradeService trades = CotaniTrades.inMemory(settlementService);

trades.createAsync(senderId, receiverId, TradeOptions.defaults())
        .thenCompose(trade -> trades.offerAsync(
                trade.id(), senderId, List.of(new TradeItem("minecraft:diamond", 1, payload))))
        .thenCompose(trade -> trades.confirmAsync(trade.id(), senderId));
```

The module never edits Bukkit inventories. `TradeSettlementService` must reserve and transfer both immutable offers
atomically using `TradeId` as its idempotency key. Any offer change clears both confirmations. If confirmation fails
with `TradeSettlementPendingException`, reconcile the existing trade; do not create a replacement for either player.
Close with `closeAsync()` during plugin shutdown.

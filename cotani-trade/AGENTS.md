# cotani-trade module instructions

## Scope

This module owns immutable two-player trade sessions. It does not retain Bukkit objects or mutate inventories
asynchronously. The actual item/currency exchange belongs to an injected `TradeSettlementService`.

## Rules

1. Require two distinct player UUIDs for every trade.
2. Keep offers immutable and replace them when a player edits their offer.
3. Any offer change invalidates both confirmations.
4. Settle only after both participants confirm the same revision.
5. Settlement adapters must be atomic and idempotent using the trade id as the operation identity.
6. Persist the pending settlement state before invoking the settlement adapter.
7. Treat expired, cancelled, completed, and failed trades as inactive.
8. Publish events as best effort after state persistence.
9. Close through `closeAsync()` and compose shutdown with the plugin lifecycle.

## Validation

```bash
./gradlew :trade:spotlessApply :trade:check
./gradlew check integrationTest
```

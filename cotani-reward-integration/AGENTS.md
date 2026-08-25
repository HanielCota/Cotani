# cotani-reward-integration module instructions

- Use CotaniRewards.settlement(...) with economy and inventory grant handlers.
- Derive currency idempotency from the claim and grant index; never invent a new operation ID during recovery.
- Apply inventory grants through InventorySyncService on the player's entity thread.
- Use a custom durable item handler when marked items can be consumed or moved before crash recovery.


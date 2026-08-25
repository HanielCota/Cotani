# cotani-inventory module instructions

- Capture and apply Player state only through InventorySyncService entity-thread operations.
- Use UUID and immutable InventorySnapshot values across async persistence flows.
- Register CotaniInventories.migrations() before starting storage-backed inventory persistence.
- Use TransferLease and completeTransferAsync(...) for cross-server handoffs; do not release locks manually.


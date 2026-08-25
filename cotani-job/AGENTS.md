# cotani-job module instructions

- Register every named handler before calling `recoverPendingAsync()`.
- Keep handlers Bukkit-free and pass immutable identifiers or values through `JobExecutionContext`.
- Preserve the logical `JobId` across retries and make side effects idempotent.
- Preserve the `JobExecutionId` across retries; recurring occurrences receive a new execution id.
- Use `cancelAsync` when durable cancellation is required; shutdown must leave pending records recoverable.
- Cancellation during a running handler waits for the original `CompletionStage` before removing persistence.
- Keep one active owner per store; distributed ownership requires a claim/lease adapter.
- Use an explicit `PersistentTaskStore` and compose all scheduler/storage work through `CompletionStage`.

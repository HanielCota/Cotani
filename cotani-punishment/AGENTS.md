# cotani-punishment module instructions

## Scope

This module owns immutable moderation records and asynchronous persistence contracts. It does not retain Bukkit/Paper
objects and does not register commands or listeners.

## Rules

1. Use `PunishmentId` as the idempotency key for retries.
2. Keep expiration derived from `Instant` values; do not schedule blocking sleeps or poll from the service.
3. Persist before exposing a newly applied or revoked punishment to queries.
4. Implement repository filters and cursor pagination in SQL; never load complete punishment history into the service.
5. Keep repository operations asynchronous and apply the configured timeout at the service boundary.
6. Treat audit integration as an optional side effect with deterministic audit IDs.
7. Check permission in the command/use-case layer before calling `PunishmentService`; do not make the domain service a
   global permission locator.
8. Use `CotaniPunishments.migrations()` before starting a Storage-backed repository.
9. Close the service through `Cotani.forPlugin(plugin).withAsync(service::closeAsync)`.

## Validation

```bash
./gradlew :punishment:spotlessApply :punishment:check
./gradlew check integrationTest
```

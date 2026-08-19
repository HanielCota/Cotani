# cotani-event

## Scope

Reflection-free event bus, listener dispatching and typed event pipelines for Paper/Folia plugins.

## Hard rules

1. Never run blocking operations (DB, HTTP, heavy IO) inside synchronous listeners; use `publishAsync` or transition via `TaskChain`.
2. Always release subscriptions by calling `subscription.unsubscribe()` or `eventBus.unsubscribe(subscription)` when disposing listeners.
3. Close the `EventBus` on plugin disable (`eventBus.close()`) to shutdown owned executors.
4. Use immutable records or value objects for event payloads; do not capture live Bukkit entities in async event flows.

## Patterns

### Bus creation

```java
EventBus eventBus = DefaultEventBus.create(
    LoggingEventExceptionHandler.usingJavaLogger(),
    scheduler.asyncExecutor()
);
```

### Cancellable event declaration

```java
public final class PlayerTeleportPreEvent extends AbstractCancellableEvent implements CotaniEvent {
    private final UUID playerId;
    // ...
}
```

## Anti-patterns

- Blocking inside synchronous listener handlers.
- Calling `isCancelled()` or `setCancelled(boolean)` instead of `cancelled()` and `cancel()`.
- Forgetting to close the bus on plugin shutdown.

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`

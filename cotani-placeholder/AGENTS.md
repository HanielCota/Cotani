# cotani-placeholder

## Scope

High-performance string token scanner, custom expansion registry, async placeholder resolution, and bidirectional bridge with PlaceholderAPI for Paper and Folia.

## Hard rules

1. Register `CotaniPlaceholders.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Do not use blocking operations (`future.join()`, `future.get()`) inside custom expansions or handlers.
3. For asynchronous placeholder lookups (e.g. database, remote HTTP, Redis), use `registerAsync` and `parseAsync`.
4. Capture immutable identifiers (`UUID`) inside `PlaceholderContext` instead of retaining live `Player` objects across async stages.

## Patterns

### Registering and Resolving Async Placeholders

```java
placeholders.registerAsync("balance", (ctx, params) -> {
    UUID uuid = ctx.viewerId();
    if (uuid == null) {
        return CompletableFuture.completedFuture("0");
    }
    return economyService.getBalanceAsync(uuid).thenApply(BigDecimal::toPlainString);
});

placeholders.parseAsync(player, "Your balance is: {balance}").thenAccept(text -> {
    player.sendMessage(Component.text(text));
});
```

## Anti-patterns

- Calling database queries synchronously inside standard `register(...)` handlers; use `registerAsync(...)` instead.
- Blocking on `parseAsync` using `.toCompletableFuture().join()`.

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`
- `java-engineering-standards`

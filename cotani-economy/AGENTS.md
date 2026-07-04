# cotani-economy

## Scope

Economy API with `BigDecimal` values, atomic transactions, idempotency and events.

## Hard rules

1. Always pass a unique `EconomyOperationId` per logical operation; generate it before the service call.
2. Use `EconomyReason` to describe the cause of every transaction.
3. Handle domain exceptions in the async pipeline; do not swallow failures.
4. Route every balance change through `EconomyService`; never mutate balances directly.
5. Use `BigDecimal` for amounts; avoid `double`/`float` for money.

## Patterns

### Bootstrap

```java
var context = new EconomyModule.Context(plugin, storage, scheduler);
var module = CotaniEconomy.create(context);
EconomyService economy = module.economyService();
```

### Deposit

```java
EconomyOperationId operationId = EconomyOperationId.random();
EconomyReason reason = EconomyReason.system("reward");

economy.deposit(userId, BigDecimal.valueOf(100), reason, operationId)
    .thenAccept(transaction -> { /* ... */ });
```

### Transfer with error handling

```java
economy.transfer(sourceId, targetId, BigDecimal.valueOf(50), reason, operationId)
    .whenComplete((transaction, error) -> {
        if (error instanceof InsufficientFundsException) {
            // notify source
        }
    });
```

## Anti-patterns

- Reusing the same `EconomyOperationId` for different operations.
- Using `double` for monetary values.
- Updating balances in SQL without going through `EconomyService`.

## Related skills

- `java-async-standards`
- `java-api-standards`

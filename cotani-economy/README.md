# cotani-economy

Economy API with `BigDecimal` values, atomic transactions, idempotency and events.

## Overview

`cotani-economy` provides a robust, async-first financial system for Paper servers. By using exact-precision `BigDecimal` arithmetic instead of volatile floating-point types (`double`/`float`), it guarantees transactional accuracy. It supports database ledger entry storage, transaction rollback capability, and strict idempotency checks to prevent double-spending exploits.

## Features

- **Exact Precision**: Powered by `BigDecimal` to prevent float/double rounding errors.
- **Idempotent Transactions**: Every transaction requires a unique `EconomyOperationId` to prevent duplication bugs.
- **Atomic Operations**: Balances are updated via ACID-compliant atomic transactions.
- **Auditable Ledger**: Keeps records of all transactions (`deposit`, `withdraw`, `transfer`) along with structured metadata (`EconomyReason`).
- **Domain-Specific Exceptions**: Easily handle errors like `InsufficientFundsException` or `InvalidAmountException` in your async chains.

## Usage

### 1. Initializing the Economy Module

Set up the module using your database storage, plugin reference, and task scheduler:

```java
var context = new EconomyModule.Context(plugin, storage, scheduler);
var module = CotaniEconomy.create(context);
EconomyService economy = module.economyService();
```

### 2. Processing a Deposit

Add funds asynchronously with idempotency guarantees:

```java
EconomyOperationId operationId = EconomyOperationId.random();
EconomyReason reason = EconomyReason.system("pvp_reward");

economy.deposit(userId, BigDecimal.valueOf(100), reason, operationId)
    .thenAccept(transaction -> {
        // Transaction succeeded
        plugin.getLogger().info("Deposited 100 coins to " + userId);
    });
```

### 3. Safe Transfer with Exception Handling

Perform a currency transfer between players, dealing with logical errors within the async pipeline:

```java
EconomyOperationId operationId = EconomyOperationId.random();
EconomyReason reason = EconomyReason.player("pay");

economy.transfer(sourceId, targetId, BigDecimal.valueOf(50), reason, operationId)
    .whenComplete((transaction, error) -> {
        if (error != null) {
            if (error instanceof InsufficientFundsException) {
                // Notify source of insufficient funds
            } else if (error instanceof InvalidAmountException) {
                // Notify player of invalid amount input
            } else {
                // Handle system errors
            }
            return;
        }
        // Notify both players of the successful transaction
    });
```

## Hard Rules & Best Practices

1. **Strict Idempotency**: Always generate and provide a unique `EconomyOperationId` per transaction. Never reuse operation IDs across separate payments or actions.
2. **Mandatory Audit Trail**: Describe the source of every transaction with a meaningful `EconomyReason`.
3. **No Double/Float Usage**: Always represent financial amounts with `BigDecimal`. Do not use primitive floating-point structures.
4. **Service-Exclusive Operations**: Route every balance modification exclusively through the `EconomyService`. Never run direct raw SQL updates on currency tables.
5. **Swallow No Exceptions**: Propagate and handle financial failure exceptions explicitly. Swallowing failures leads to out-of-sync player balances.

## Anti-Patterns

- Generating a single `EconomyOperationId` and caching it to reuse for multiple sequential transactions.
- Performing arithmetic with `double` amounts and casting the result to `BigDecimal` at the end.
- Directly mutating the balance column in database rows without going through the economy pipeline.

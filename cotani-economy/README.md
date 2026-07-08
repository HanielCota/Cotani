# cotani-economy

Transactional economy service. Supports atomic deposit/withdraw operations, transfer checks, history logs, and thread-safe balance caching using `BigDecimal`.

## Usage

```java
EconomyService economy = module.economyService();

economy.deposit(
    userId,
    BigDecimal.valueOf(100),
    EconomyReason.system("reward"),
    EconomyOperationId.random()
).thenAccept(transaction -> {
    // Deposit completed
});
```

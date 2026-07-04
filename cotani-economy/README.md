# cotani-economy

Módulo de economia para o Cotani. Projetado como API/framework modular, não como um plugin de comandos. Entrega uma API pública pequena, implementação padrão em memória para testes e abstrações internas para storage transacional.

## Responsabilidade

- Expor `EconomyService` como API pública assíncrona.
- Usar `BigDecimal` para valores monetários com scale controlado.
- Garantir atomicidade em withdraw, deposit e transfer.
- Validar amounts, scale e limites de saldo.
- Usar `operationId` para idempotência.
- Manter transaction log obrigatório.
- Fornecer cache de leitura com TTL e invalidação automática em escritas.
- Publicar eventos de economia na main thread.

## Stack

- Java 21+
- Paper API
- JSpecify
- Caffeine (cache)
- `cotani-core`, `cotani-task`, `cotani-storage`, `cotani-config`

## Uso básico

```java
var context = new EconomyModule.Context(plugin, storage, scheduler);
var module = CotaniEconomy.create(context);
EconomyService economy = module.economyService();

var operationId = EconomyOperationId.random();
var reason = EconomyReason.system("starter_reward");

economy.deposit(userId, BigDecimal.valueOf(100), reason, operationId)
    .thenAccept(transaction -> { /* ... */ });
```

## Bootstrap rápido (in-memory)

```java
var bootstrap = EconomyBootstrap.createDefault();
EconomyService economy = bootstrap.service();

economy.deposit(userId, BigDecimal.valueOf(100), reason, operationId);
```

## Transferência

```java
economy.transfer(sourceId, targetId, BigDecimal.valueOf(50), reason, operationId)
    .thenAccept(transaction -> { /* ... */ })
    .exceptionally(error -> {
        if (error instanceof InsufficientFundsException) {
            // saldo insuficiente
        }
        return null;
    });
```

## Moeda

```java
EconomyCurrency coins = new EconomyCurrency(
    CurrencyId.of("coins"),
    "Coins",
    "C",
    2
);

EconomySettings settings = EconomySettings.defaultSettings(coins);
```

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `CotaniEconomy` | Fachada estática para criar o módulo. |
| `EconomyModule` | Lifecycle handle: expõe `EconomyService` e `close()`. |
| `EconomyService` | API assíncrona: `balance`, `has`, `deposit`, `withdraw`, `set`, `transfer`. |
| `EconomyTransaction` | Registro imutável de uma transação. |
| `EconomyReason` | Motivo/identificador da transação. |
| `EconomyOperationId` | ID idempotente de operação. |
| `EconomyTransactionId` | ID único da transação no log. |
| `EconomyTransactionType` | Tipo: DEPOSIT, WITHDRAW, SET, TRANSFER. |
| `EconomyBalance` | Saldo de uma conta. |
| `EconomyAccount` | Conta economica. |
| `EconomyCurrency` / `CurrencyId` / `EconomyFormatter` | Modelos de moeda. |
| `EconomySettings` | Configurações de saldo inicial, máximo, cache, etc. |
| `EconomyEventPublisher` / `EconomyTransactionEvent` | Eventos de economia. |
| `EconomyException` e subclasses | Domínio de exceções: `InsufficientFundsException`, `InvalidAmountException`, `MaximumBalanceExceededException`, `DuplicateEconomyOperationException`, `SameEconomyAccountTransferException`. |
| `EconomyBootstrap` | Bootstrap in-memory para testes/desenvolvimento. |

## Estrutura de pacotes

```text
com.cotani.economy
├── CotaniEconomy.java
├── EconomyService.java
├── EconomyBootstrap.java
├── EconomySettings.java
├── api
│   └── EconomyModule.java
├── account
│   ├── EconomyAccount.java
│   └── EconomyBalance.java
├── currency
│   ├── EconomyCurrency.java
│   ├── CurrencyId.java
│   └── EconomyFormatter.java
├── transaction
│   ├── EconomyTransaction.java
│   ├── EconomyTransactionId.java
│   ├── EconomyTransactionType.java
│   ├── EconomyOperationId.java
│   └── EconomyReason.java
├── event
│   ├── EconomyTransactionEvent.java
│   └── EconomyEventPublisher.java
├── exception
│   ├── EconomyException.java
│   ├── InsufficientFundsException.java
│   ├── InvalidAmountException.java
│   ├── MaximumBalanceExceededException.java
│   ├── DuplicateEconomyOperationException.java
│   └── SameEconomyAccountTransferException.java
└── internal
    ├── DefaultEconomyModule.java
    ├── service
    │   └── DefaultEconomyService.java
    ├── cache
    │   └── CachedEconomyService.java
    ├── storage
    │   ├── SqlEconomyStore.java
    │   ├── InMemoryEconomyStore.java
    │   ├── EconomyStorageMappers.java
    │   └── CreateEconomyTablesMigration.java
    ├── repository
    │   ├── EconomyAccountRepository.java
    │   ├── EconomyTransferRepository.java
    │   └── EconomyAccountKey.java
    ├── event
    │   ├── MainThreadEconomyEventPublisher.java
    │   ├── BukkitEconomyEventPublisher.java
    │   └── NoopEconomyEventPublisher.java
    ├── protection
    │   ├── EconomyGuard.java
    │   └── DefaultEconomyGuard.java
    └── config
        └── EconomyConfiguration.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":economy"))
}
```

## Integração

- Requer `cotani-storage` para persistência SQL.
- Requer `cotani-task` para execução assíncrona.
- Pode usar `cotani-config` para carregar `EconomySettings`.
- Pode usar `cotani-user` para resolver jogadores.

## Garantias do storage

- Atualizar saldo e inserir transaction log na mesma transação.
- Impedir saldo negativo no banco.
- Usar `operation_id` único.
- Transferir saldo de forma atômica.
- Não rodar I/O na main thread.

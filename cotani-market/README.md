# cotani-market

Marketplace assíncrono para anúncios de itens, consultas paginadas e compras idempotentes.

O módulo é Bukkit-free no domínio. A entrega do item e a cobrança são responsabilidade de um
`MarketSettlementService` fornecido pelo plugin, que deve usar `EconomyService`, um serviço de inventário
na thread da entidade e, quando necessário, `MailService` para entrega posterior.

## Uso básico

```java
var market = CotaniMarkets.fromRepository(
        new StorageMarketRepository(storage),
        settlementService,
        eventBus,
        MarketServiceOptions.defaults());

var listing = market.listAsync(
        sellerId,
        new MarketItem("minecraft:diamond", 3, serializedItem),
        new MarketPrice(CurrencyId.of("coins"), new BigDecimal("12.50")),
        Duration.ofHours(24));
```

O exemplo acima pressupõe que as migrations foram registradas antes de `CotaniStorage.startAsync()`:

```java
storageBuilder.migrations(StorageMarketRepository.migrations().toArray(Migration[]::new));
```

## Compra e recuperação

```java
var request = MarketPurchaseRequest.create(listingId, buyerId);

market.purchaseAsync(request).thenAccept(purchase -> {
    // A cobrança e a entrega já foram confirmadas pelo adapter.
}).exceptionally(failure -> {
    if (failure.getCause() instanceof MarketPurchasePendingException) {
        // Repetir com o mesmo request/purchaseId após recuperar o adapter.
    }
    return null;
});
```

O anúncio é reservado antes da liquidação. Se o adapter falhar ou exceder o timeout, a compra permanece
`PENDING`; nunca gere outro `MarketPurchaseId` para a mesma tentativa. O adapter deve tornar cobrança e
entrega idempotentes pelo `MarketPurchaseId`.

`pendingPurchasesAsync(limit)` permite recuperar compras pendentes após reinício. A confirmação durable só
acontece depois que o settlement adapter conclui com sucesso.

Quando o adapter conseguir provar que não houve efeito externo, use
`releasePendingAsync(purchaseId)`. O método consulta `statusAsync` e só libera a reserva para estados
`NOT_STARTED` ou `FAILED`; estados `UNKNOWN` e `IN_PROGRESS` permanecem pendentes por segurança.
Use `purgeAsync(before)` em uma rotina de manutenção com retenção explícita: a limpeza remove recibos
terminais e encerra a garantia de idempotência desses identificadores.

## Contratos importantes

- `MarketItem` guarda somente snapshot serializado, nunca `ItemStack` vivo.
- `MarketRepository` não acessa Bukkit e pode ser fakeado nos testes.
- `StorageMarketRepository` usa transações para reservar anúncios e finalizar compras.
- Consultas têm paginação e limites configuráveis.
- `MarketService` coordena somente compras com o mesmo `MarketPurchaseId`; operações independentes não ficam
  bloqueadas por uma liquidação externa pendente e novas operações são rejeitadas após `closeAsync()`.
- Eventos são opcionais e publicados de forma best effort.

O módulo não manipula diretamente inventários ou jogadores; a transição para a thread da entidade pertence ao
adapter de settlement, mantendo o domínio seguro para Paper e Folia.

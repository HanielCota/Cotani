# cotani-market

## Regras do módulo

- Mantenha o domínio em `com.cotani.market.api` sem dependência de Bukkit.
- Use `MarketPurchaseId` como chave estável de idempotência para cobrança e entrega.
- Nunca marque uma compra como `SETTLED` antes de o `MarketSettlementService` concluir.
- Compras pendentes devem ser recuperáveis com o mesmo id; não crie uma nova tentativa automaticamente.
- Implemente `statusAsync` no adapter quando for possível reconciliar efeitos externos; o padrão `UNKNOWN` nunca
  libera uma reserva.
- Use `releasePendingAsync` apenas para liberar compras com status externo `NOT_STARTED` ou `FAILED`.
- Execute `purgeAsync` com uma política de retenção explícita, pois ele encerra a idempotência dos recibos removidos.
- Repositórios devem usar `CompletionStage` e transações para reservas concorrentes.
- `MarketItem` contém snapshot imutável; não armazene `Player`, `ItemStack` ou `Inventory`.
- A entrega deve ser feita pelo adapter host na thread Paper/Folia correta; o serviço do mercado permanece Bukkit-free.
- Limite paginação e recuperação de pendências para evitar carga ilimitada.
- Feche o serviço com `closeAsync()` no lifecycle do plugin.

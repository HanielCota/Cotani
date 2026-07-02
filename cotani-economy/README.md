# cotani-economy

Módulo de economia para o Cotani.

Este módulo foi desenhado como API/framework modular, não como um plugin de comandos.
Ele entrega uma API pública pequena, implementação padrão em memória para testes/desenvolvimento e abstrações internas para storage transacional.

## O que contém

- API pública `EconomyService`
- `BigDecimal` para valores monetários
- `record` para modelos imutáveis
- validações fortes de amount, scale e limites
- `operationId` para idempotência
- transaction log obrigatório
- withdraw atômico
- transfer atômica
- cache não incluído na primeira versão para evitar inconsistência prematura
- implementação in-memory para testes ou bootstrap inicial
- sem `join`, `get` ou `Thread.sleep`
- domínio sem dependência de Bukkit/Paper

## Uso básico

```java
var module = EconomyModule.createDefault();
var economy = module.service();

var operationId = EconomyOperationId.random();
var reason = EconomyReason.system("starter_reward");

economy.deposit(userId, BigDecimal.valueOf(100), reason, operationId);
```

## Como integrar ao Cotani

No projeto principal, substitua `InMemoryEconomyStore` por uma implementação baseada no `cotani-storage`.
A implementação de storage precisa manter as mesmas garantias:

- atualizar saldo e inserir transaction log na mesma transação;
- impedir saldo negativo no banco;
- usar `operation_id` único;
- transferir saldo de forma atômica;
- não rodar I/O na main thread.

# cotani-economy

Módulo de economia para o Cotani.

Este módulo foi desenhado como API/framework modular, não como um plugin de comandos.
Ele entrega uma API pública pequena, implementação padrão em memória para testes/desenvolvimento e abstrações internas
para storage transacional.

## O que contém

- API pública `EconomyService`
- `BigDecimal` para valores monetários
- `record` para modelos imutáveis
- validações fortes de amount, scale e limites
- `operationId` para idempotência
- transaction log obrigatório
- withdraw atômico
- transfer atômica
- cache de leitura de saldo com TTL e invalidação automática em escritas
- implementação in-memory para testes ou bootstrap inicial (`EconomyBootstrap`)
- implementação SQL via `SqlEconomyStore`
- sem `join`, `get` ou `Thread.sleep` em código de aplicação
- domínio sem dependência de Bukkit/Paper

## Uso básico

```java
var bootstrap = EconomyBootstrap.createDefault();
var economy = bootstrap.service();

var operationId = EconomyOperationId.random();
var reason = EconomyReason.system("starter_reward");

economy.deposit(userId, BigDecimal.valueOf(100), reason, operationId);
```

## Como integrar ao Cotani

No projeto principal, use `CotaniEconomy.create(context)` para obter um módulo totalmente configurado
com storage SQL, cache e publicação de eventos na main thread.

A implementação de storage mantém as seguintes garantias:

- atualizar saldo e inserir transaction log na mesma transação;
- impedir saldo negativo no banco;
- usar `operation_id` único;
- transferir saldo de forma atômica;
- não rodar I/O na main thread.

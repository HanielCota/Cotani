---
id: package-architecture
title: Arquitetura de pacotes
sidebar_label: Arquitetura de pacotes
description: Convenções para organizar módulos, pacotes e classes do Cotani.
---

# Arquitetura de pacotes

O Cotani é um monorepo modular. Cada módulo representa uma capacidade de domínio ou uma preocupação transversal,
mas também precisa ter uma organização interna previsível. O objetivo desta convenção é fazer com que uma classe tenha
um lugar óbvio e que a implementação não vaze para os consumidores.

## Estrutura canônica

Cada módulo deve seguir esta forma:

```text
com.cotani.<modulo>/
  Cotani<Modulo>.java          # fachada/factory pública do módulo
  package-info.java
  api/                         # contratos públicos e modelos expostos
    <Modulo>Service.java
    <Modulo>Request.java
    <Modulo>Exception.java
    event/                     # eventos públicos do domínio, quando existirem
  spi/                         # pontos de extensão públicos e deliberados
    StorageContext.java
  internal/                    # implementação não pública do módulo
    Default<Modulo>Service.java
    application/               # casos de uso e orquestração, se necessário
    domain/                    # regras e modelos privados, se necessário
    persistence/               # adapters de repositório e migrations
    platform/                  # Paper/Folia listeners e adapters
    cache/                     # cache interno, quando necessário
    mapping/                   # conversões entre API, storage e plataforma
```

Os subpacotes de `internal` são opcionais. Eles só devem ser criados quando houver mais de uma responsabilidade real;
um módulo pequeno pode manter sua implementação diretamente em `internal`.

## Regras de visibilidade

| Pacote | Pode conter | Não pode conter |
| --- | --- | --- |
| `com.cotani.<modulo>` | fachada, factory e `package-info.java` | regra de negócio, repository concreto ou listener |
| `api` | interfaces, records, value objects, exceptions, enums e eventos públicos | imports de `internal`, `impl` ou detalhes de storage |
| `spi` | portas de extensão estáveis para adapters e repositories | lifecycle da fachada ou estado mutável da implementação |
| `internal` | services concretos, composição e estado privado | dependências de implementação de outro módulo |
| `internal.persistence` | SQL, repositories concretos, codecs e migrations privadas | API Paper ou regra de apresentação |
| `internal.platform` | listeners e adapters Paper/Folia | acesso a storage diretamente |

`storage` fora de `internal` só deve existir quando o módulo expõe deliberadamente um SPI de persistência, como
migrations que o plugin consumidor precisa registrar. Caso contrário, use `internal.persistence`.

## Nomes proibidos como organização

Não criar novos pacotes `impl`, `util`, `helper`, `manager`, `common` ou `misc`. Eles escondem a responsabilidade da
classe. Use o papel real: `application`, `persistence`, `platform`, `cache`, `mapping`, `policy` ou um nome de domínio.

Classes concretas devem ser `final` por padrão. Implementações internas públicas por necessidade técnica devem ser
marcadas com `@InternalApi` e nunca podem ser importadas por outro módulo.

## Fluxo de dependências

```text
consumidor
    -> fachada do módulo / api
    -> internal.application
    -> internal.domain
    -> ports em api
    -> adapters em internal.persistence ou internal.platform
```

A direção é sempre para dentro. A API não conhece implementação; a implementação conhece a API. Um módulo também só
conhece a API de outro módulo, nunca seus pacotes `internal`.

## Paper, Folia e async

Listeners e adapters extraem IDs e dados imutáveis, delegam para um caso de uso e não carregam `Player`, `World`,
`Entity`, `Inventory` ou `Block` para fluxos assíncronos. I/O fica em `internal.persistence` e a transição de volta para
a thread global, de região ou de entidade fica concentrada no scheduler do módulo `cotani-task`.

## Migração incremental

Não vamos mover todos os módulos em uma única alteração. A ordem segura é:

1. congelar a criação de novas estruturas fora desta convenção;
2. separar adapters de persistência e plataforma quando um módulo crescer;
3. remover imports de implementação entre módulos;
4. fortalecer a validação Gradle para impedir regressões;
5. atualizar READMEs e exemplos após cada mudança estrutural.

Todos os módulos que possuíam `impl` foram migrados para `internal`. A validação Gradle agora impede que novos pacotes
`impl` sejam introduzidos.

---
id: module-index
title: Índice de módulos
sidebar_label: Índice de módulos
description: Mapa dos módulos Cotani e seus principais casos de uso.
---

# Índice de módulos

Os módulos são independentes e podem ser combinados conforme o domínio do plugin. Cada módulo possui README próprio,
testes e pacotes `api` documentados no [Javadoc](https://hanielcota.github.io/Cotani/api/).

Os módulos continuam separados internamente para preservar dependências pequenas, testes isolados e versionamento.
Para quem consome a biblioteca, pense neles como seis grupos de produto:

| Grupo | Quando usar |
| --- | --- |
| Foundation | lifecycle, execução assíncrona, eventos, texto e itens |
| Infrastructure | configuração, storage, cache, Redis, jobs e métricas |
| Player | usuários, permissões, idioma, economia, cooldown e inventário |
| World | teleporte, locais, regiões, NPCs, displays e HUD |
| Social | parties, amizades, filas, trades e mail |
| Gameplay | recompensas, quests, achievements, temporadas, estatísticas, rankings e marketplace |

### Foundation

| Módulo | Use quando precisar de |
| --- | --- |
| [`cotani-core`](https://github.com/HanielCota/Cotani/tree/master/cotani-core) | lifecycle e composição de recursos |
| [`cotani-task`](https://github.com/HanielCota/Cotani/tree/master/cotani-task) | schedulers explícitos e transições Paper/Folia |
| [`cotani-job`](https://github.com/HanielCota/Cotani/tree/master/cotani-job) | jobs persistentes, retries, recorrência e recuperação |
| [`cotani-text`](https://github.com/HanielCota/Cotani/tree/master/cotani-text) | Adventure, MiniMessage e mensagens |
| [`cotani-item`](https://github.com/HanielCota/Cotani/tree/master/cotani-item) | construção de itens imutáveis |
| [`cotani-locale`](https://github.com/HanielCota/Cotani/tree/master/cotani-locale) | catálogos e preferências de idioma |

### Infrastructure

| Módulo | Use quando precisar de |
| --- | --- |
| [`cotani-config`](https://github.com/HanielCota/Cotani/tree/master/cotani-config) | configuração tipada e reload assíncrono |
| [`cotani-storage`](https://github.com/HanielCota/Cotani/tree/master/cotani-storage) | SQL, migrations e repositórios |
| [`cotani-cache`](https://github.com/HanielCota/Cotani/tree/master/cotani-cache) | cache persistente com autosave |
| [`cotani-redis`](https://github.com/HanielCota/Cotani/tree/master/cotani-redis) | locks, eventos e dados distribuídos |
| [`cotani-metrics`](https://github.com/HanielCota/Cotani/tree/master/cotani-metrics) | métricas e exportação Prometheus |

### Player, World, Social e Gameplay

| Grupo | Módulos |
| --- | --- |
| Player | `cotani-user`, `cotani-permission`, `cotani-locale`, `cotani-economy`, `cotani-cooldown`, `cotani-inventory` |
| World | `cotani-teleport`, `cotani-location`, `cotani-region`, `cotani-npc`, `cotani-display`, `cotani-hud`, `cotani-nametag`, `cotani-gui`, `cotani-command`, `cotani-dialog`, `cotani-placeholder` |
| Social | `cotani-party`, `cotani-friend`, `cotani-queue`, `cotani-trade`, `cotani-mail` |
| Gameplay | `cotani-reward`, `cotani-reward-integration`, `cotani-quest`, `cotani-statistics`, `cotani-ranking`, `cotani-achievement`, `cotani-season`, `cotani-market`, `cotani-punishment` |

`cotani-cleanup` é operacional e `cotani-audit`/`cotani-audit-storage` são módulos de observabilidade e histórico.

`cotani-cleanup` oferece manutenção operacional segura com preview, políticas explícitas e remoção em threads de região.

`cotani-market` oferece marketplace persistente com anúncios paginados, reservas transacionais, compras idempotentes e
recuperação segura de liquidações pendentes.

Consulte a pasta do módulo para o contrato específico, opções de configuração, exceções e exemplos:

```text
https://github.com/HanielCota/Cotani/tree/master/cotani-<modulo>
```

## Como escolher

1. Comece pelo grupo de produto, não pela implementação.
2. Escolha o módulo específico dentro do grupo.
3. Adicione as dependências de infraestrutura que o módulo exige.
4. Use o BOM para alinhar versões.
5. Leia o contrato assíncrono antes de integrar operações que fazem I/O.

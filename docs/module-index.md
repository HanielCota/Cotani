---
id: module-index
title: Índice de módulos
sidebar_label: Índice de módulos
description: Mapa dos módulos Cotani e seus principais casos de uso.
---

# Índice de módulos

Os módulos são independentes e podem ser combinados conforme o domínio do plugin. Cada módulo possui README próprio,
testes e pacotes `api` documentados no [Javadoc](https://hanielcota.github.io/Cotani/api/).

## Fundação e execução

| Módulo | Use quando precisar de |
| --- | --- |
| [`cotani-core`](https://github.com/HanielCota/Cotani/tree/master/cotani-core) | lifecycle e composição de recursos |
| [`cotani-task`](https://github.com/HanielCota/Cotani/tree/master/cotani-task) | schedulers explícitos e transições Paper/Folia |
| [`cotani-text`](https://github.com/HanielCota/Cotani/tree/master/cotani-text) | Adventure, MiniMessage e mensagens |
| [`cotani-item`](https://github.com/HanielCota/Cotani/tree/master/cotani-item) | construção de itens imutáveis |
| [`cotani-locale`](https://github.com/HanielCota/Cotani/tree/master/cotani-locale) | catálogos e preferências de idioma |

## Infraestrutura e persistência

| Módulo | Use quando precisar de |
| --- | --- |
| [`cotani-config`](https://github.com/HanielCota/Cotani/tree/master/cotani-config) | configuração tipada e reload assíncrono |
| [`cotani-storage`](https://github.com/HanielCota/Cotani/tree/master/cotani-storage) | SQL, migrations e repositórios |
| [`cotani-cache`](https://github.com/HanielCota/Cotani/tree/master/cotani-cache) | cache persistente com autosave |
| [`cotani-redis`](https://github.com/HanielCota/Cotani/tree/master/cotani-redis) | locks, eventos e dados distribuídos |
| [`cotani-metrics`](https://github.com/HanielCota/Cotani/tree/master/cotani-metrics) | métricas e exportação Prometheus |

## Gameplay e domínio

`cotani-user`, `cotani-economy`, `cotani-cooldown`, `cotani-teleport`, `cotani-location`, `cotani-event`,
`cotani-command`, `cotani-gui`, `cotani-display`, `cotani-hud`, `cotani-nametag`, `cotani-npc`, `cotani-region`,
`cotani-dialog`, `cotani-permission`, `cotani-placeholder`, `cotani-inventory`, `cotani-party`, `cotani-friend`,
`cotani-queue`, `cotani-trade`, `cotani-punishment`, `cotani-mail`, `cotani-reward` e `cotani-reward-integration` oferecem
casos de uso de jogador, gameplay, moderação, comunicação e entrega idempotente de recompensas.

Consulte a pasta do módulo para o contrato específico, opções de configuração, exceções e exemplos:

```text
https://github.com/HanielCota/Cotani/tree/master/cotani-<modulo>
```

## Como escolher

1. Comece pelo caso de uso, não pela implementação.
2. Adicione o módulo de domínio necessário.
3. Adicione as dependências de infraestrutura que o módulo exige.
4. Use o BOM para alinhar versões.
5. Leia o contrato assíncrono antes de integrar operações que fazem I/O.

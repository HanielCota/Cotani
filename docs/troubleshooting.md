---
id: troubleshooting
title: Solução de problemas
sidebar_label: Solução de problemas
description: Verificações rápidas para problemas comuns na integração do Cotani.
---

# Solução de problemas

## A classe não é encontrada em runtime

Confirme que o módulo está no classpath do plugin e que as dependências transitivas foram empacotadas ou estão
disponíveis no servidor. Use o BOM e evite misturar versões diferentes dos módulos.

## A operação terminou na thread errada

Uma `CompletionStage` não garante a thread de conclusão. Antes de acessar `Player`, `World`, `Entity`, `Inventory` ou
outros objetos Paper, faça uma transição explícita usando `TaskChain` ou `PaperTaskScheduler`.

## O servidor apresenta lentidão durante o comando

Verifique se o comando está usando `.executesAsync` para I/O, consultas e orquestração pesada. Comandos que precisam
alterar entidades devem usar a transição de entidade somente no trecho que toca a API Paper.

## Dados do jogador ficam desatualizados

Não mantenha referências a `Player` em serviços, caches ou callbacks assíncronos. Armazene `UUID` e snapshots imutáveis;
re-resolva o jogador quando a operação retornar à thread proprietária.

## O shutdown não termina corretamente

Componha os estágios de `closeAsync()` e `closeAsync()` dos módulos que o plugin criou. Não substitua o fechamento por
`join()`, `get()` ou espera manual.

## O build da documentação falha

Execute as verificações separadamente para identificar a origem:

```bash
./gradlew check
./gradlew aggregateJavadoc
cd website
npm ci
npm run build
```

Links para APIs públicas devem apontar para o Javadoc ou para uma página da Wiki, e exemplos devem continuar compiláveis
em `docs-examples`.

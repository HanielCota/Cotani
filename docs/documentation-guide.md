---
id: documentation-guide
title: Guia de documentação
sidebar_label: Guia de documentação
description: Padrões para criar, revisar e manter a Wiki do Cotani.
---

# Guia de documentação

A documentação faz parte do contrato do projeto. Mudanças em APIs públicas devem atualizar código, testes e documentação
no mesmo pull request sempre que possível.

## Estrutura de uma página

Prefira esta ordem:

1. objetivo e quando usar;
2. pré-requisitos;
3. instalação e configuração;
4. exemplo mínimo;
5. comportamento assíncrono e threads;
6. falhas e limitações;
7. links para API e exemplos relacionados.

## Padrões de conteúdo

- use linguagem direta e exemplos pequenos;
- explique contratos, não detalhes acidentais da implementação;
- prefira `CompletionStage` a `CompletableFuture` nas APIs públicas;
- nunca ensine `join()`, `get()`, `Thread.sleep()` ou acesso assíncrono a Bukkit/Paper;
- não capture objetos vivos como `Player` em fluxos assíncronos;
- mantenha exemplos compiláveis sempre que forem apresentados como completos;
- use links relativos dentro da Wiki e links absolutos para APIs externas ou módulos não publicados no site.

## Checklist de pull request

- [ ] título e descrição da página estão claros;
- [ ] navegação/sidebar foi atualizada quando necessário;
- [ ] exemplos foram compilados;
- [ ] `npm run build` passou;
- [ ] `./gradlew aggregateJavadoc` passou quando a API mudou;
- [ ] links e nomes de módulos refletem a versão atual;
- [ ] conteúdo obsoleto foi removido ou marcado como migração.

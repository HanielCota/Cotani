---
id: README
title: Visão geral
sidebar_label: Visão geral
description: Visão geral da documentação e dos recursos do Cotani.
---

<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220" />

# Cotani documentation

</div>

Esta é a fonte Markdown da Wiki do Cotani. Os READMEs dos módulos continuam sendo a referência mais próxima da
instalação e das APIs específicas; esta página organiza os conceitos compartilhados e os guias de integração.

## Guias principais

- [Começando](getting-started.md): instalação, dependências e pré-requisitos.
- [Quickstart](quickstart.md): um plugin mínimo com bootstrap e transição para a entity thread.
- [Arquitetura](architecture.md): limites dos módulos, lifecycle e transições Paper/Folia.
- [Arquitetura de pacotes](package-architecture.md): convenções para organizar classes e responsabilidades dentro de cada módulo.
- [Contratos assíncronos](async-contracts.md): conclusão, timeout, cancelamento e shutdown.
- [Índice de módulos](module-index.md): escolha do módulo adequado para cada caso de uso.
- [Cookbook](ai/cotani-cookbook.md): padrões prontos para integração.
- [Solução de problemas](troubleshooting.md): falhas comuns e verificações rápidas.
- [Prontidão para produção](production-readiness.md): staging, soak test, observabilidade, falhas e rollback.
- [Guia de documentação](documentation-guide.md): padrão para criar e manter páginas.

## Generated API reference

O Javadoc agregado é gerado por `./gradlew aggregateJavadoc` e publicado em `/api/` pelo workflow do GitHub Pages. Ele
não é versionado no repositório.

## Verification

Os exemplos que devem compilar ficam em [`docs-examples`](https://github.com/HanielCota/Cotani/tree/master/docs-examples). Execute:

```bash
./gradlew check
```

Para exemplos que dependem de banco de dados, execute também as suítes com Docker:

```bash
./gradlew integrationTest
```

Para a validação completa antes de publicar uma versão:

```bash
./gradlew releaseVerification
```

Para visualizar a Wiki localmente:

```bash
cd website
npm ci
npm run start
```

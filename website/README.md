# Cotani Wiki

Este diretório contém o site da documentação do Cotani, gerado com Docusaurus.

## Desenvolvimento local

Requer Node.js 20 ou superior.

```bash
cd website
npm ci
npm run start
```

O conteúdo exibido pelo site vem de [`../docs`](../docs). O Javadoc é gerado separadamente pelo Gradle e publicado em
`/api/` no GitHub Pages.

## Verificação

```bash
npm ci
npm run build
```

## Publicação

O workflow [`javadoc.yml`](../.github/workflows/javadoc.yml) gera a Wiki e o Javadoc em um único artefato
do GitHub Pages.

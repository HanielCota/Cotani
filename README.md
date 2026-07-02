# Java Agent Pack

Pacote com `AGENTS.md` e skills para projetos Java, Paper/PaperSpigot e arquitetura modular.

## Conteúdo

```text
AGENTS.md
.agents/
  skills/
    java-engineering-standards/
      SKILL.md
    java-async-standards/
      SKILL.md
    paper-plugin-architecture/
      SKILL.md
    java-api-standards/
      SKILL.md
```

## Instalação no projeto

Extraia o conteúdo deste pacote na raiz do seu repositório.

Estrutura final:

```text
seu-projeto/
  AGENTS.md
  .agents/
    skills/
      java-engineering-standards/
      java-async-standards/
      paper-plugin-architecture/
      java-api-standards/
```

## Instalação global opcional

Se quiser usar as skills em todos os projetos:

```bash
mkdir -p ~/.config/agents/skills
cp -R .agents/skills/* ~/.config/agents/skills/
```

Mesmo usando global, recomendo manter o `AGENTS.md` na raiz de cada projeto.

## Uso recomendado

### Java geral

```text
/skill:java-engineering-standards revise esta classe preservando comportamento, melhorando null-safety, nomes, var, SRP e organização.
```

### Async

```text
/skill:java-async-standards revise este fluxo assíncrono. Não quero join, get, sleep, bloqueio ou executor implícito.
```

### Paper/PaperSpigot

```text
/skill:paper-plugin-architecture revise a arquitetura desse plugin Paper, incluindo lifecycle, listeners, commands, services, repositories, cache, config, mensagens e segurança de main thread.
```

### API pública/módulos

```text
/skill:java-api-standards revise essa API pública e proponha contratos mais limpos, com Optional, CompletionStage, value objects, result types e separação api/impl.
```

## Fluxo ideal

1. Use `paper-plugin-architecture` para revisar estrutura geral.
2. Use `java-api-standards` para definir contratos públicos e módulos.
3. Use `java-engineering-standards` para refatorar classes individuais.
4. Use `java-async-standards` para revisar qualquer código com async, scheduler, banco, cache ou Paper main thread.

## Regra principal

Não use as skills como decoração. Use a skill certa para a tarefa certa.

- Async? `java-async-standards`.
- Bukkit/Paper? `paper-plugin-architecture`.
- API pública? `java-api-standards`.
- Qualidade Java geral? `java-engineering-standards`.

# Cotani

> Biblioteca modular para desenvolvimento de plugins **Paper/Spigot** em Java, com foco em arquitetura limpa, execução assíncrona segura e APIs previsíveis.

[![Build](https://img.shields.io/github/actions/workflow/status/HanielCota/Cotani/build.yml?logo=github&label=build)](https://github.com/HanielCota/Cotani/actions)
[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-00A4FF?logo=minecraft)](https://papermc.io/)
[![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?logo=gradle)](https://gradle.org/)

---

## Índice

- [Visão geral](#visão-geral)
- [Por que Cotani?](#por-que-cotani)
- [Módulos](#módulos)
- [Arquitetura](#arquitetura)
- [Primeiros passos](#primeiros-passos)
- [Exemplos](#exemplos)
- [Build e testes](#build-e-testes)
- [Estrutura do projeto](#estrutura-do-projeto)
- [AGENTS.md](#agentsmd)

---

## Visão geral

A **Cotani** é uma coleção de módulos Gradle que isolam responsabilidades comuns no desenvolvimento de plugins Minecraft: scheduling, cache, configuração, persistência, economia, teleport, usuários e utilitários de texto/item.

Cada módulo expõe uma **API pública pequena e intencional** e mantém a implementação em pacotes `internal`/`impl`. O objetivo é reduzir acoplamento, aumentar a testabilidade e garantir segurança entre threads sem sacrificar a produtividade do dia a dia.

---

## Por que Cotani?

- **Sem bloqueio em aplicação** — nenhum `join()`, `get()` ou `Thread.sleep()` no código de produção.
- **Main-thread safety** — Bukkit/Paper só é tocado na main thread; IDs imutáveis (UUID, chaves de domínio) fluem pelo async.
- **Contratos nulos claros** — `@NullMarked` por pacote, `Optional` para ausência esperada e `CompletionStage` nas APIs públicas.
- **SRP e testabilidade** — listeners, commands, services, repositories e caches com responsabilidade única.
- **Imutabilidade** — records, coleções imutáveis e value objects para modelos de domínio.
- **APIs previsíveis** — factories, builders e resultados tipados em vez de mapas genéricos ou `Object`.

---

## Módulos

| Módulo | Descrição | Ver mais |
|--------|-----------|----------|
| `cotani-core` | Bootstrap, lifecycle e exceções base. | [README](cotani-core/README.md) |
| `cotani-task` | Scheduler abstrato para Paper/Folia: async, global, region, entity, debounce, tasks persistentes e `TaskChain`. | [README](cotani-task/README.md) |
| `cotani-cache` | Cache com Caffeine: `DataCache`, `PlayerDataCache`, dirty tracking, autosave e callbacks async. | [README](cotani-cache/README.md) |
| `cotani-config` | Config YAML com binding para records, validators, serializers e reload async. | [README](cotani-config/README.md) |
| `cotani-storage` | Storage SQL (SQLite/MySQL/MariaDB) com migrations, query builder, transactions e repositories. | [README](cotani-storage/README.md) |
| `cotani-text` | MiniMessage, placeholders, componentes e serialização de texto. | [README](cotani-text/README.md) |
| `cotani-item` | Builders fluentes para `ItemStack`, `ArmorBuilder`, `SkullBuilder` e resolução de texturas. | [README](cotani-item/README.md) |
| `cotani-user` | Carregamento, cache e persistência de usuários. | [README](cotani-user/README.md) |
| `cotani-economy` | Economia com `BigDecimal`, transações atômicas, cache e eventos. | [README](cotani-economy/README.md) |
| `cotani-teleport` | Teleport async com policies, cooldown, safe-location e eventos próprios. | [README](cotani-teleport/README.md) |

---

## Arquitetura

### Fluxo async preferido

```text
evento na main thread
  -> captura IDs imutáveis
  -> executa I/O async
  -> processa dados seguros async
  -> retorna para main thread
  -> toca Bukkit/Paper API
  -> continua persistência/audit async se necessário
```

### Separação de responsabilidades

| Camada | Responsabilidade |
|--------|------------------|
| **Plugin** | Lifecycle e bootstrap apenas. |
| **Listeners** | Receber eventos, extrair valores imutáveis, validar guardas simples e delegar. |
| **Commands** | Validar sender, parsear argumentos, verificar permissões e delegar. |
| **Services** | Casos de uso e regras de negócio. |
| **Repositories** | Persistência. |
| **Caches** | Cache explícito e isolado. |

---

## Primeiros passos

### Requisitos

- Java 21+
- Paper 1.21+
- Gradle 8.5+

### Adicionar ao projeto

No `build.gradle.kts` do seu plugin, adicione os módulos que precisar:

```kotlin
dependencies {
    implementation(project(":cotani-core"))
    implementation(project(":cotani-task"))
    implementation(project(":cotani-cache"))
    implementation(project(":cotani-config"))
    implementation(project(":cotani-storage"))
    implementation(project(":cotani-text"))
    implementation(project(":cotani-item"))
}
```

Ou, ao usar como dependência externa:

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.cotani:cotani-task:1.0.0")
    implementation("com.cotani:cotani-cache:1.0.0")
}
```

### Bootstrap mínimo

```java
public final class MeuPlugin extends JavaPlugin {

    private Cotani cotani;
    private PaperTaskScheduler scheduler;

    @Override
    public void onEnable() {
        scheduler = SchedulerFactory.create(this);

        cotani = Cotani.forPlugin(this)
            .with(scheduler)
            .build();
    }

    @Override
    public void onDisable() {
        cotani.close();
    }
}
```

---

## Exemplos

### Cache de jogadores

```java
public final class MeuPlugin extends JavaPlugin {

    private PaperTaskScheduler scheduler;
    private PlayerDataCache<User> users;

    @Override
    public void onEnable() {
        scheduler = SchedulerFactory.create(this);

        users = CotaniCache.players(User.class)
            .repository(new UserRepository(scheduler))
            .defaultValue(User::createDefault)
            .preset(CachePreset.PLAYER_DATA)
            .build(this, scheduler);
    }

    @Override
    public void onDisable() {
        users.close();
        scheduler.close();
    }
}
```

### Config com records

```java
public record PluginSettings(
    @Default("false") boolean debug,
    @Default("5m") Duration autosave,
    MessageSettings messages
) {}

public record MessageSettings(
    @Default("<green>Cotani</green> <dark_gray>»</dark_gray>") Component prefix
) {}

CotaniConfigs configs = CotaniConfigs.create(plugin)
    .file("config.yml")
    .load();

PluginSettings settings = configs.file("config.yml").bindOrThrow(PluginSettings.class);
```

### Storage

```java
CotaniStorage storage = CotaniStorage.fromConfig(this, getConfig(), "storage")
    .migrations(new CreateUsersTableMigration())
    .repositories(UserRepository.class)
    .scheduler(scheduler)
    .build();

storage.startAsync()
    .thenAccept(started -> {
        UserRepository users = started.repository(UserRepository.class);
    });
```

### Economia

```java
var context = new EconomyModule.Context(plugin, storage, scheduler);
var module = CotaniEconomy.create(context);
EconomyService economy = module.economyService();

economy.deposit(
        userId,
        BigDecimal.valueOf(100),
        EconomyReason.system("reward"),
        EconomyOperationId.random())
    .thenAccept(transaction -> { /* ... */ });
```

### Teleport

```java
var module = CotaniTeleports.create(plugin, scheduler);
module.teleportService().teleport(
    TeleportRequest.builder()
        .playerId(player.getUniqueId())
        .target(location)
        .cause(TeleportCause.SPAWN)
        .source("spawn")
        .options(TeleportOptions.spawn())
        .build()
).thenAccept(result -> switch (result) {
    case TeleportResult.Success success -> player.sendMessage(Component.text("Teleportado!"));
    case TeleportResult.Failure failure -> player.sendMessage(Component.text("Falha: " + failure.reason()));
});
```

---

## Build e testes

Compilar todos os módulos:

```bash
./gradlew build
```

Rodar testes, verificações de estilo e validação de arquitetura:

```bash
./gradlew check
```

A validação de arquitetura (`validateModuleArchitecture`) garante que:

- módulos não importem implementações de outros módulos;
- pacotes `api` não importem `impl`/`internal`;
- não existam ciclos de dependência entre módulos Gradle.

---

## Estrutura do projeto

```text
Cotani/
├── cotani-core/        # Lifecycle e contratos base
├── cotani-task/        # Scheduling e TaskChain
├── cotani-cache/       # Cache com Caffeine
├── cotani-config/      # Configuração YAML tipada
├── cotani-storage/     # Persistência SQL
├── cotani-text/        # Utilitários de texto Adventure
├── cotani-item/        # Builders de ItemStack
├── cotani-user/        # Carregamento e cache de usuários
├── cotani-economy/     # Sistema de economia
├── cotani-teleport/    # Teleport async com policies
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── AGENTS.md
└── README.md
```

---

## AGENTS.md

Este repositório inclui um [`AGENTS.md`](AGENTS.md) com as regras de arquitetura, async, design de API e estilo Java adotados pelo projeto. As skills correspondentes estão em [`.agents/skills`](.agents/skills).

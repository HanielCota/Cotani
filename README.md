# Cotani

> Biblioteca modular para desenvolvimento de plugins **Paper/Spigot** em Java, com foco em arquitetura limpa, async seguro e APIs previsíveis.

[![Build](https://img.shields.io/github/actions/workflow/status/HanielCota/Cotani/build.yml?logo=github&label=build)](https://github.com/HanielCota/Cotani/actions)
[![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-1.21%2B-00A4FF?logo=minecraft)](https://papermc.io/)
[![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?logo=gradle)](https://gradle.org/)

---

## Visão geral

A **Cotani** é uma coleção de módulos que isolam responsabilidades comuns em plugins Minecraft: cache, configuração, storage, tasks, economia, teleport, usuários e utilitários de texto/item. Cada módulo expõe uma API pública pequena e mantém a implementação em pacotes `internal`/`impl`.

### Princípios

- **Sem bloqueio em aplicação** — nada de `join()`, `get()` ou `Thread.sleep()` no código de produção.
- **Main-thread safety** — Bukkit/Paper só é tocado na main thread; IDs imutáveis fluem pelo async.
- **Contratos nulos claros** — `@NullMarked` por pacote, `Optional` e `CompletionStage` nas APIs públicas.
- **SRP e testabilidade** — services, repositories, listeners e commands com responsabilidade única.
- **Imutabilidade** — records, coleções imutáveis e value objects para domínio.

---

## Módulos

| Módulo | Descrição |
|--------|-----------|
| [`cotani-core`](cotani-core) | Bootstrap, lifecycle e exceções base. |
| [`cotani-task`](cotani-task) | Scheduler abstrato para Paper/Folia: async, global, region, entity, debounce, tasks persistentes. |
| [`cotani-cache`](cotani-cache) | Cache com Caffeine: `DataCache`, `PlayerDataCache`, dirty tracking, autosave e callbacks async. |
| [`cotani-config`](cotani-config) | Config YAML com binding para records, validators, serializers e reload async. |
| [`cotani-storage`](cotani-storage) | Storage SQL (SQLite/MySQL/MariaDB) com migrations, query builder, transactions e repositories. |
| [`cotani-text`](cotani-text) | MiniMessage, placeholders, componentes e serialização de texto. |
| [`cotani-item`](cotani-item) | Builders para `ItemStack`, `ArmorBuilder`, `SkullBuilder` e resolução de texturas. |
| [`cotani-user`](cotani-user) | Carregamento, cache e persistência de usuários. |
| [`cotani-economy`](cotani-economy) | Economia com `BigDecimal`, transações atômicas, cache e eventos. |
| [`cotani-teleport`](cotani-teleport) | Teleport async com policies, cooldown, safe-location e eventos próprios. |

---

## Exemplos

### Cache de jogadores

```java
public final class MeuPlugin extends JavaPlugin {

    private PaperTaskScheduler scheduler;
    private PlayerDataCache<User> users;

    @Override
    public void onEnable() {
        scheduler = CotaniTasks.create(this);

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

var configs = CotaniConfigs.create(plugin)
    .file("config.yml")
    .load();

PluginSettings settings = configs.file("config.yml").bindOrThrow(PluginSettings.class);
```

### Storage

```java
CotaniStorage storage = CotaniStorage.fromConfig(this, getConfig(), "storage", scheduler)
    .migrations(new CreateUsersTableMigration())
    .repositories(UserRepository.class)
    .build()
    .start();

UserRepository users = storage.repository(UserRepository.class);
```

### Economia

```java
var module = CotaniEconomy.create(context);
EconomyService economy = module.economyService();

economy.deposit(userId, BigDecimal.valueOf(100), EconomyReason.system("reward"), EconomyOperationId.random())
    .thenAccept(result -> /* ... */);
```

### Teleport

```java
CotaniTeleport.teleports().teleport(
    TeleportRequest.builder()
        .player(player)
        .target(location)
        .cause(TeleportCause.SPAWN)
        .options(TeleportOptions.spawn())
        .build()
).thenAccept(result -> switch (result) {
    case TeleportResult.Success success -> player.sendMessage(text("Teleportado!"));
    case TeleportResult.Failure failure -> player.sendMessage(text("Falha: " + failure.reason()));
});
```

---

## Build

```bash
./gradlew build
```

Para rodar testes e verificações:

```bash
./gradlew check
```

---

## Estrutura do projeto

```text
Cotani/
├── cotani-core/
├── cotani-task/
├── cotani-cache/
├── cotani-config/
├── cotani-storage/
├── cotani-text/
├── cotani-item/
├── cotani-user/
├── cotani-economy/
├── cotani-teleport/
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## AGENTS.md

Este repositório inclui um [`AGENTS.md`](AGENTS.md) com regras de arquitetura, async, API e estilo Java. As skills correspondentes estão em [`.agents/skills`](.agents/skills).


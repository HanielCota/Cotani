# cotani-cache

Módulo de cache para plugins Paper/Spigot, usando Caffeine internamente. Foco em simplicidade no uso diário, dirty tracking automático, autosave e integração nativa com o scheduler assíncrono da Cotani.

## Responsabilidade

- Oferecer `DataCache<K, V>` genérico e `PlayerDataCache<V>` especializado para jogadores.
- Gerenciar carga, descarga, persistência e evicção de entradas.
- Rastrear entradas "sujas" e salvá-las automaticamente.
- Integrar-se a `PaperTaskScheduler` para executar I/O fora da main thread.
- Fornecer presets (`PLAYER_DATA`, `TEMPORARY`, `STATIC_DATA`, `HIGH_ACTIVITY`) para cenários comuns.

## Stack

- Java 21+
- Paper API
- Caffeine
- JSpecify
- `cotani-task`

## Uso básico

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

## Operações comuns

```java
User user = users.get(player);
Optional<User> maybe = users.find(player);

users.getOrLoadAsync(player.getUniqueId())
    .thenAccept(loaded -> { /* ... */ });

users.updateAsync(player.getUniqueId(), user -> user.addCoins(100))
    .thenAccept(updated -> { /* ... */ });

users.mutateAsync(player.getUniqueId(), user -> user.addCoins(100));

users.saveAsync(player.getUniqueId());
```

## Cache genérico

```java
DataCache<UUID, Instant> cooldowns = CotaniCache.temporary(
    UUID.class,
    Instant.class,
    Duration.ofSeconds(30)
).defaultValue(Instant::now)
 .build(scheduler);
```

## Repository de cache

```java
public final class UserRepository implements CacheRepository<UUID, User> {

    @Override
    public CompletionStage<Optional<User>> find(UUID key) {
        // carrega do banco
    }

    @Override
    public CompletionStage<Void> save(UUID key, User value) {
        // persiste no banco
    }

    @Override
    public CompletionStage<Void> delete(UUID key) {
        // remove do banco
    }
}
```

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `CotaniCache` | Fábrica estática de builders. |
| `DataCache<K, V>` | Cache genérico assíncrono. |
| `PlayerDataCache<V>` | Cache especializado para dados de jogadores. |
| `DataCacheBuilder<K, V>` | Builder para cache genérico. |
| `PlayerDataCacheBuilder<V>` | Builder para cache de jogadores. |
| `CacheRepository<K, V>` | Contrato de persistência do cache. |
| `CacheSettings` / `CacheSettingsBuilder` | Configurações de expiração, autosave, stats, etc. |
| `CachePreset` | Presets prontos (`PLAYER_DATA`, `TEMPORARY`, `STATIC_DATA`, `HIGH_ACTIVITY`). |
| `CacheStatsView` | Estatísticas do cache. |
| `CacheException` / `CacheLoadException` / `CacheSaveException` | Exceções do domínio. |

## Estrutura de pacotes

```text
com.cotani.cache
├── CotaniCache.java
├── api
│   ├── DataCache.java
│   ├── PlayerDataCache.java
│   └── PlayerValueFactory.java
├── builder
│   ├── DataCacheBuilder.java
│   └── PlayerDataCacheBuilder.java
├── internal
│   └── caffeine
│       ├── CaffeineDataCache.java
│       └── CaffeinePlayerDataCache.java
├── repository
│   ├── CacheRepository.java
│   └── NoopCacheRepository.java
├── policy
│   ├── CachePreset.java
│   ├── CacheSettings.java
│   └── CacheSettingsBuilder.java
├── entry
│   └── CacheEntry.java
├── stats
│   └── CacheStatsView.java
├── listener
│   └── PlayerDataCacheListener.java
└── exception
    ├── CacheException.java
    ├── CacheLoadException.java
    └── CacheSaveException.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":cache"))
}
```

## Integração

- Requer `cotani-task` para scheduling.
- Pode usar `cotani-storage` via `CacheRepository` para persistência SQL.

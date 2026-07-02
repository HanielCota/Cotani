# Cotani Cache

Modulo de cache para plugins Paper/Spigot usando Caffeine internamente.

O foco do modulo e deixar o uso diario simples:

```java
users.get(player);
users.getOrLoad(player);
users.update(player, user -> user.addCoins(100));
users.save(player);
```

## Principais recursos

- Caffeine como engine interna
- `DataCache<K, V>` generico
- `PlayerDataCache<V>` para dados de jogadores
- `CacheRepository<K, V>` para integrar com storage
- `CacheFuture<T>` para callbacks async/global/entity/region e ponte para `TaskChain`
- integracao direta com `PaperTaskScheduler` do `cotani-task`
- dirty tracking automatico
- `update` para entidades imutaveis
- `mutate` para entidades mutaveis
- autosave
- save on quit
- unload on quit
- save on evict
- stats
- presets
- zero uso do keyword `else` no codigo Java

## Dependencia

```kotlin
dependencies {
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}
```

## Uso com PlayerDataCache

```java
public final class MeuPlugin extends JavaPlugin {

    private PaperTaskScheduler scheduler;
    private PlayerDataCache<User> users;

    @Override
    public void onEnable() {
        this.scheduler = SchedulerFactory.create(this);
        var repository = new UserRepository(scheduler);

        this.users = CotaniCache.players(User.class)
            .repository(repository)
            .defaultValue(User::createDefault)
            .preset(CachePreset.PLAYER_DATA)
            .build(this, scheduler);
    }

    @Override
    public void onDisable() {
        users.saveAll().join();
        users.close();
        scheduler.close();
    }
}
```

## Update imutavel

```java
users.update(player, user -> user.addCoins(100))
    .thenEntity(player, updated -> {
        player.sendMessage(Component.text("Coins: " + updated.coins()));
    });
```

## Mutate para objeto mutavel

```java
users.mutate(player, user -> {
    user.addCoins(100);
});
```

## Cache generico

```java
DataCache<UUID, Instant> cooldowns = CotaniCache.temporary(
    UUID.class,
    Instant.class,
    Duration.ofSeconds(30)
).defaultValue(Instant::now)
 .build(scheduler);
```

## Estrutura

```txt
com.cotani.cache
├── api
├── builder
├── caffeine
├── entry
├── exception
├── future
├── listener
├── policy
├── repository
├── stats
└── example
```

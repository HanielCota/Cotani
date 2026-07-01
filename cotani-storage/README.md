# Cotani Storage

Modulo de storage moderno para plugins Paper/Folia.

## Objetivo

Fornecer uma camada simples, assincrona e legivel para persistencia em SQLite, MySQL e MariaDB.

## Recursos

- Java 21
- API fluent
- SQLite, MySQL e MariaDB
- HikariCP
- Query builder
- Schema builder
- Migrations
- Transactions
- StorageFuture
- Callbacks para entity, region, global e async
- Row wrapper
- ParameterBinder
- Serializers
- Repository base
- Suporte a Paper/Folia scheduler
- Early return em todo fluxo de decisao no codigo Java

## Exemplo rapido

```java
this.storage = CotaniStorage.fromConfig(this, getConfig(), "storage")
    .migrations(new CreateUsersTableMigration())
    .repositories(UserRepository.class)
    .start();

this.users = storage.repository(UserRepository.class);
```

```java
users.findOrCreate(player)
    .map(user -> user.addCoins(100))
    .flatMap(users::save)
    .thenEntity(player, unused -> player.sendMessage(Component.text("Coins adicionados.")));
```

## Config

```yaml
storage:
  type: SQLITE
  sqlite:
    file: database.db
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: ""
    pool-size: 10
```

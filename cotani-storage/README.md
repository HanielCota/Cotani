# cotani-storage

Módulo de persistência SQL moderno para plugins Paper/Folia. Suporta SQLite, MySQL e MariaDB com HikariCP, migrations, query builder, transações e repositories.

## Responsabilidade

- Abstrair conexões com SQLite, MySQL e MariaDB.
- Executar queries e transações de forma assíncrona.
- Fornecer query builders fluentes (`SelectQuery`, `UpdateQuery`, `DeleteQuery`, `UpsertQuery`, `ExistsQuery`).
- Gerenciar migrations de schema.
- Oferecer `CotaniRepository` como base para repositories do domínio.
- Garantir que I/O de banco nunca rode na main thread.

## Stack

- Java 21+
- Paper API
- HikariCP
- MySQL Connector, MariaDB Java Client, SQLite JDBC (runtime)
- JSpecify
- `cotani-task`, `cotani-text`

## Uso básico

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

## Configuração YAML

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

## Query builder

```java
storage.table("users")
    .select()
    .where("uuid", uuid)
    .single()
    .thenAccept(maybeRow -> maybeRow.ifPresent(row -> {
        String name = row.getString("username");
    }));
```

## Transaction

```java
storage.transactions().run(context -> {
    context.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amount, sourceId);
    context.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, targetId);
}).thenAccept(result -> { /* ... */ });
```

## Repository

```java
public final class UserRepository extends CotaniRepository {

    public UserRepository(CotaniStorage storage) {
        super(storage);
    }

    public CompletionStage<Optional<User>> findByUuid(UUID uuid) {
        return table("users")
            .select()
            .where("uuid", uuid)
            .single()
            .thenApply(maybeRow -> maybeRow.map(this::map));
    }
}
```

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `CotaniStorage` | Ponto central: backend, executor, dialect, schema, transações, repositories. |
| `CotaniStorageBuilder` | Builder fluente para configurar backend, threads, scheduler, migrations, repositories. |
| `StorageBackend` / `StorageCredentials` | Abstração de backend e credenciais. |
| `QueryExecutor` | Executor de queries assíncrono. |
| `TableQuery` | Acesso a operações de tabela. |
| `SelectQuery` / `UpdateQuery` / `DeleteQuery` / `UpsertQuery` / `ExistsQuery` | Query builders. |
| `Row` | Wrapper de uma linha de resultado. |
| `TransactionManager` / `TransactionContext` | Gerenciamento de transações. |
| `Migration` / `MigrationRunner` | Migrations de schema. |
| `Schema` / `TableSchema` / `ColumnDefinition` / `ColumnType` | Definição de schema. |
| `SqlDialect` / `DialectFactory` / `SQLiteDialect` / `MySqlDialect` / `MariaDbDialect` | Dialects SQL. |
| `CotaniRepository` / `CrudRepository` / `PlayerDataRepository` | Bases de repository. |
| `ValueSerializer` / `ValueSerializerRegistry` | Serialização de valores para SQL. |
| `StorageFuture` / `OptionalStorageFuture` | Futures customizados com callbacks async/global/region/entity. |
| `StorageError` / `StorageException` / `QueryError` / `MigrationError` / `TransactionError` | Erros tipados. |

## Estrutura de pacotes

```text
com.cotani.storage
├── api
│   ├── CotaniStorage.java
│   └── CotaniStorageBuilder.java
├── backend
│   ├── StorageBackend.java
│   ├── StorageCredentials.java
│   ├── SQLiteBackend.java
│   ├── MySqlBackend.java
│   └── MariaDbBackend.java
├── provider
│   ├── StorageProvider.java
│   ├── StorageProviderFactory.java
│   ├── SQLiteStorageProvider.java
│   └── HikariStorageProvider.java
├── executor
│   └── QueryExecutor.java
├── query
│   ├── TableQuery.java
│   ├── SelectQuery.java
│   ├── UpdateQuery.java
│   ├── DeleteQuery.java
│   ├── UpsertQuery.java
│   ├── ExistsQuery.java
│   ├── Row.java
│   └── ParameterBinder.java
├── transaction
│   ├── TransactionManager.java
│   └── TransactionContext.java
├── migration
│   ├── Migration.java
│   └── MigrationRunner.java
├── schema
│   ├── Schema.java
│   ├── TableSchema.java
│   ├── ColumnDefinition.java
│   └── ColumnType.java
├── dialect
│   ├── SqlDialect.java
│   ├── DialectFactory.java
│   ├── SQLiteDialect.java
│   ├── MySqlDialect.java
│   └── MariaDbDialect.java
├── repository
│   ├── CotaniRepository.java
│   ├── CrudRepository.java
│   └── PlayerDataRepository.java
├── serializer
│   ├── ValueSerializer.java
│   └── ValueSerializerRegistry.java
├── future
│   ├── StorageFuture.java
│   └── OptionalStorageFuture.java
├── error
│   ├── StorageError.java
│   ├── StorageException.java
│   ├── QueryError.java
│   ├── MigrationError.java
│   └── TransactionError.java
└── config
    └── StorageConfigReader.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":storage"))
}
```

## Integração

- Requer `cotani-task` para scheduling assíncrono.
- `cotani-user` e `cotani-economy` dependem deste módulo para persistência.

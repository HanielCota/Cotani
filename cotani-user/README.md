# cotani-user

Módulo de gerenciamento de ciclo de vida de usuários: carregamento assíncrono, cache online, persistência via `cotani-storage` e autosave.

## Responsabilidade

- Carregar e cachear dados de jogadores ao entrar no servidor.
- Persistir dados automaticamente em intervalos configuráveis e no quit.
- Fornecer uma API pública assíncrona (`UserService`) para localizar usuários por UUID.
- Fornecer migrations de schema para a tabela de usuários.

## Stack

- Java 21+
- Paper API
- JSpecify
- `cotani-storage`, `cotani-task`, `cotani-core`, `cotani-text`

## Uso básico

```java
public final class MeuPlugin extends JavaPlugin {

    private UserModule users;

    @Override
    public void onEnable() {
        var storage = CotaniStorage.create(this)
            .sqlite("database.db")
            .migrations(CotaniUsers.migrations().toArray(new Migration[0]))
            .scheduler(scheduler)
            .build();

        users = CotaniUsers.create(this, storage, scheduler);
    }

    @Override
    public void onDisable() {
        users.close();
    }
}
```

## API pública

```java
users.userService()
    .findAsync(player.getUniqueId())
    .thenAccept(maybeUser -> maybeUser.ifPresent(user -> {
        // user.uniqueId(), user.username(), user.firstJoinAt(), ...
    }));
```

## UserModuleOptions

```java
var options = new UserModuleOptions(
    true,                                    // autoSaveEnabled
    Duration.ofMinutes(5),                   // autoSaveInterval
    MiniMessages.parse("<red>Erro ao carregar dados.") // loadFailureMessage
);

users = CotaniUsers.create(this, storage, scheduler, options);
```

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `CotaniUsers` | Fachada estática para criar o módulo e obter migrations. |
| `UserModule` | Lifecycle handle: expõe `UserService` e `close()`. |
| `UserService` | API assíncrona: `findAsync`, `getOrThrowAsync`, `isLoadedAsync`. |
| `CotaniUser` | Visão read-only de um usuário carregado. |
| `UserModuleOptions` | Configurações de autosave e mensagem de falha. |
| `CreateUsersTableMigration` | Migration padrão da tabela de usuários. |

## Estrutura de pacotes

```text
com.cotani.user
├── CotaniUsers.java
├── api
│   ├── UserModule.java
│   ├── UserService.java
│   ├── CotaniUser.java
│   └── UserModuleOptions.java
└── internal
    ├── DefaultUserModule.java
    ├── service
    │   ├── InternalUserService.java
    │   └── SimpleUserService.java
    ├── cache
    │   └── UserCache.java
    ├── repository
    │   ├── UserRepository.java
    │   └── StorageUserRepository.java
    ├── mapper
    │   └── UserMapper.java
    ├── listener
    │   └── UserListener.java
    └── model
        └── SimpleCotaniUser.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":user"))
}
```

## Integração

- Requer `cotani-storage` para persistência.
- Requer `cotani-task` para autosave assíncrono.
- `cotani-economy` pode usar `UserService` para resolver contas.

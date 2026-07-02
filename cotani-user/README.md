# cotani-user

Módulo base de usuários para plugins Paper.

## Responsabilidade

- Carregar ou criar usuário no join.
- Manter usuários carregados em cache por UUID.
- Salvar e remover do cache no quit.
- Salvar todos os usuários carregados no shutdown.
- Expor uma API pública pequena para outros módulos.

## API pública

- `com.cotani.user.api.CotaniUser`
- `com.cotani.user.api.UserModule`
- `com.cotani.user.api.UserModuleOptions`
- `com.cotani.user.api.UserService`
- `com.cotani.user.api.UserNotLoadedException`
- `com.cotani.user.CotaniUsers`

Outros módulos devem depender apenas dessas classes.

## Bootstrap

Registre as migrations do user antes de iniciar o storage:

```java
PaperTaskScheduler scheduler = CotaniTasks.create(plugin);

CotaniStorage storage = CotaniStorage.fromConfig(plugin, plugin.getConfig(), "storage", scheduler)
        .migrations(CotaniUsers.migrations().toArray(Migration[]::new))
        .build()
        .start();

UserModule users = CotaniUsers.create(plugin, storage, scheduler);
UserService userService = users.userService();
```

No shutdown, feche o `UserModule` antes de fechar o `CotaniStorage`.

```java
users.close();
storage.close();
scheduler.close();
```

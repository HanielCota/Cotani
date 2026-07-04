# cotani-user

## Scope

User lifecycle: async loading, online cache and persistence.

## Hard rules

1. Resolve users through `UserService` (`findAsync`, `getOrThrowAsync`); do not use `Bukkit.getOfflinePlayer(...)` in async flows.
2. Do not store live `Player` references in services or repositories; store `UUID` and re-resolve on the main thread when needed.
3. Register the module's migrations in `CotaniStorage` before starting it.
4. Call `UserModule.close()` on shutdown to flush pending saves.

## Patterns

### Bootstrap

```java
CotaniStorage storage = CotaniStorage.create(this)
    .sqlite("database.db")
    .migrations(CotaniUsers.migrations().toArray(new Migration[0]))
    .scheduler(scheduler)
    .build();

UserModule users = CotaniUsers.create(this, storage, scheduler);
```

### Find user

```java
users.userService().findAsync(uuid)
    .thenAccept(maybeUser -> maybeUser.ifPresent(user -> {
        // user.uniqueId(), user.username(), ...
    }));
```

## Anti-patterns

- Calling `Bukkit.getPlayer(uuid)` from async code.
- Storing `Player` in long-lived objects.
- Loading user data synchronously on `PlayerJoinEvent`.

## Related skills

- `java-async-standards`
- `paper-plugin-architecture`

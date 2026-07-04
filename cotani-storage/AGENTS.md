# cotani-storage

## Scope

SQL persistence (SQLite/MySQL/MariaDB) with migrations, query builder, transactions and repositories.

## Hard rules

1. Start storage asynchronously with `startAsync()`; never call `.join()`.
2. Keep all database I/O off the main thread; compose through `CompletionStage`.
3. Use `TransactionManager` for operations that modify multiple rows/tables.
4. Register migrations in strict creation order; never skip versions.
5. Repository classes must extend `CotaniRepository` and be registered via `CotaniStorageBuilder.repositories(...)`.

## Patterns

### Build and start

```java
CotaniStorage storage = CotaniStorage.fromConfig(this, getConfig(), "storage")
    .migrations(new CreateUsersTableMigration())
    .repositories(UserRepository.class)
    .scheduler(scheduler)
    .build();

storage.startAsync().thenAccept(started -> {
    UserRepository users = started.repository(UserRepository.class);
});
```

### Query builder

```java
storage.table("users")
    .select()
    .where("uuid", uuid)
    .single()
    .thenAccept(maybeRow -> maybeRow.ifPresent(row -> { /* ... */ }));
```

### Transaction

```java
storage.transactions().run(context -> {
    context.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amount, sourceId);
    context.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, targetId);
});
```

## Anti-patterns

- Raw `Statement`/`ResultSet` manipulation in services or commands.
- Running storage queries synchronously on the main thread.
- Mutating shared state inside async storage callbacks without returning to the main thread.

## Related skills

- `java-async-standards`
- `java-api-standards`

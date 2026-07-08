# cotani-storage

SQL persistence (SQLite/MySQL/MariaDB) with migrations, query builder, transactions and repositories.

## Overview

`cotani-storage` acts as the persistence engine for Cotani modules. Supporting SQLite, MySQL, and MariaDB drivers, it provides a lightweight database query builder, transactional control flows, auto-run schema migrations, and clean repository implementations. All operations are designed to execute off the server main thread.

## Features

- **Multi-Driver Persistence**: SQLite for local testing and MySQL/MariaDB for production scale.
- **Dynamic Migrations**: Integrated database schema migrations executed at initialization.
- **Fluent Query Builder**: Query, update, insert, and delete rows without writing verbose raw SQL statements.
- **Asynchronous Pipeline**: Full support for async operations returning Java `CompletionStage` APIs.
- **Transaction Management**: Perform atomic updates across multiple rows and tables securely.
- **Clean Repositories**: Repository wrappers extend `CotaniRepository` for decoupled data mapping.

## Usage

### 1. Initialization and Startup

Build the storage configuration and boot the engine asynchronously:

```java
CotaniStorage storage = CotaniStorage.fromConfig(this, getConfig(), "storage")
    .migrations(new CreateUsersTableMigration())
    .repositories(UserRepository.class)
    .scheduler(scheduler)
    .build();

// Start asynchronously - DO NOT block plugin startup with join()
storage.startAsync().thenAccept(started -> {
    UserRepository users = started.repository(UserRepository.class);
    plugin.getLogger().info("Database engine started, repositories bound!");
});
```

### 2. Query Builder Operations

Retrieve rows asynchronously using the built-in query builder:

```java
storage.table("users")
    .select()
    .where("uuid", uuid)
    .single()
    .thenAccept(maybeRow -> {
        maybeRow.ifPresent(row -> {
            String username = row.getString("username");
            // Process row data safely
        });
    });
```

### 3. Transaction Runner

Apply updates atomically within a transaction block:

```java
storage.transactions().run(context -> {
    // These statements run atomically on the database connection pool
    context.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amount, sourceId);
    context.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, targetId);
    return null; // Return value if needed
}).whenComplete((result, error) -> {
    if (error != null) {
        plugin.getLogger().severe("Transaction failed: " + error.getMessage());
    }
});
```

## Hard Rules & Best Practices

1. **Non-Blocking Startup**: Always start the storage system using `.startAsync()`. Never invoke `.join()` or `.get()` to wait for initialization during startup.
2. **Off-Thread Operations**: Keep all persistence I/O operations away from the Minecraft server main thread. Compose queries using `CompletionStage`.
3. **Transaction Safety**: Always use the `TransactionManager` when modifying multiple rows or tables to maintain database state integrity.
4. **Migration Sequence**: Register migration scripts in sequential, creation order. Never modify already executed migrations or skip version indices.
5. **Decoupled Architecture**: Encapsulate persistence logic inside repositories extending `CotaniRepository`.

## Anti-Patterns

- Processing database queries synchronously inside event listeners or commands.
- Mutating shared Bukkit/Paper state directly inside async database callback lambdas. Return to the main thread first.
- Manipulating raw `Statement` or `ResultSet` classes inside service interfaces or plugins.

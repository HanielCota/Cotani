# cotani-storage

SQL database manager supporting SQLite, MySQL, and MariaDB. Includes connection pooling via HikariCP, query builders, asynchronous migrations, and repository mappings.

## Usage

```java
CotaniStorage storage = CotaniStorage.fromConfig(plugin, config, "storage")
    .migrations(new CreateTablesMigration())
    .scheduler(scheduler)
    .build();

storage.startAsync().thenAccept(started -> {
    // Database connection active
});
```

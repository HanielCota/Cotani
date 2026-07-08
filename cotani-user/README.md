# cotani-user

User lifecycle: async loading, online cache and persistence.

## Overview

`cotani-user` manages player sessions, caching user states while they are online, and persisting details securely to a database. It ensures that operations like loading user profiles are handled asynchronously on login events to eliminate main-thread server freezes.

## Features

- **Decoupled User Profiles**: Tracks user states using custom entities (`CotaniUser`) rather than binding fields directly to Bukkit's player models.
- **Asynchronous Startup Loading**: Pre-loads profile objects in background threads during connection handshake.
- **Auto-Flush Persistence**: Flushes changed fields to SQLite/MySQL databases when users disconnect or during server reloads.
- **Clean API Operations**: Service layer supports asynchronous queries like `findAsync` and `getOrThrowAsync`.

## Usage

### 1. Initialization and Schema Setup

Configure database storage, include migrations, and build the user module:

```java
import com.cotani.user.CotaniUsers;
import com.cotani.user.api.UserModule;

CotaniStorage storage = CotaniStorage.create(plugin)
    .sqlite("database.db")
    .migrations(CotaniUsers.migrations().toArray(new Migration[0])) // Bind user table migrations
    .scheduler(scheduler)
    .build();

UserModule usersModule = CotaniUsers.create(plugin, storage, scheduler);
```

### 2. Loading User Profiles Asynchronously

Request profile details asynchronously through the user service:

```java
usersModule.userService().findAsync(userId)
    .thenAccept(maybeUser -> {
        maybeUser.ifPresent(user -> {
            // Retrieve data fields safely
            String name = user.username();
            UUID uuid = user.uniqueId();
        });
    });
```

## Hard Rules & Best Practices

1. **Service Resolutions**: Resolve profile data queries through the `UserService` (`findAsync`, `getOrThrowAsync`). Do not call blocking operations like `Bukkit.getOfflinePlayer(...)` inside async pipelines.
2. **Entity Isolation**: Never store live `Player` references in services, database repositories, or async tasks. Retain the user's `UUID` instead and resolve the player reference on the main thread only when touching Paper/Spigot APIs.
3. **Database Pre-requisites**: Always bind user migrations to `CotaniStorage` before starting the database engine.
4. **Clean Disabling**: Ensure you invoke `UserModule.close()` inside the plugin's `onDisable` lifecycle to flush cached data and execute final database updates.

## Anti-Patterns

- Accessing `Bukkit.getPlayer(uuid)` or similar Bukkit manager calls inside async execution lanes.
- Retaining Bukkit player entities inside long-lived caches or singletons (causes massive memory leaks).
- Loading user profiles synchronously inside high-frequency listeners like `PlayerJoinEvent`.

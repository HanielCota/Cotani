# cotani-config

YAML configuration mapping framework. Binds files to immutable Java `record`s with built-in validation, custom type serializers, and asynchronous reloads.

## Overview

`cotani-config` replaces the boilerplate-heavy and error-prone process of parsing config files using Bukkit's built-in APIs. By leveraging modern Java `record`s and custom annotations, it maps YAML configuration files to type-safe, validated objects.

## Features

- **Immutable Mapping**: Maps files directly to immutable Java `record` classes.
- **Built-in Validation**: Declares constraint annotations like `@Required`, `@Range`, and `@Default` to catch parsing errors early.
- **Asynchronous Reloads**: Supports reloading configuration directories asynchronously to keep the main server thread lag-free.
- **Custom Serializers**: Built-in support for mapping game-specific types like `Duration`, `Location`, and colors.

## Usage

### 1. Declaring a Config Record

Define the configuration schema using annotations for defaults and ranges:

```java
import com.cotani.config.annotation.Default;
import com.cotani.config.annotation.Range;
import java.time.Duration;

public record PluginSettings(
    @Default("false") boolean debug,
    @Default("5m") Duration autosaveInterval,
    @Range(min = 1, max = 100) @Default("10") int maxConnections
) {}
```

### 2. Loading and Binding Configs

Initialize the configurations builder and bind files:

```java
CotaniConfigs configs = CotaniConfigs.create(plugin)
    .file("config.yml")
    .load();

// Bind the config file to your settings record
PluginSettings settings = configs.file("config.yml").bindOrThrow(PluginSettings.class);
```

### 3. Asynchronous Config Reloading

Reload config directories dynamically and rebind configuration objects asynchronously:

```java
configs.reloadAsync()
    .thenRun(() -> {
        // Rebind variables inside the completion callback
        this.settings = configs.file("config.yml").bindOrThrow(PluginSettings.class);
        plugin.getLogger().info("Configuration reloaded successfully.");
    })
    .toCompletionStage();
```

## Hard Rules & Best Practices

1. **Immutability Only**: All configuration structures must be declared as immutable `record`s. Never expose setters or use mutable classes.
2. **Annotation-based Validation**: Avoid writing manual null, empty, or bounds checking. Use `@Default`, `@Required`, `@Range`, and `@ConfigPath` to validate configurations declarations.
3. **Validate Immediately**: Validate settings at startup and throw detailed messages to abort the startup sequence if configurations are invalid.
4. **Async Reload boundaries**: Always reload configuration files asynchronously. Never execute blocking file I/O operations directly on the server's main thread.

## Anti-Patterns

- Creating mutable config POJOs with setters.
- Scattering raw calls to `getConfig().getString(...)` throughout the codebase.
- Executing synchronous file reads during server execution.

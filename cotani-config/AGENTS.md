# cotani-config

## Scope

Typed YAML configuration with record binding, validation and async reload.

## Hard rules

1. Configuration types must be immutable `record`s.
2. Use `@Default`, `@Required`, `@Range` and `@ConfigPath` instead of manual null/empty checks.
3. Validate configs on load and report issues explicitly.
4. Reload configs asynchronously with `reloadAsync()`; never block the main thread during reload.
5. Do not mutate bound config records; reload and rebind when values change.

## Patterns

### Record config

```java
public record PluginSettings(
    @Default("false") boolean debug,
    @Default("5m") Duration autosave,
    MessageSettings messages
) {}
```

### Load and bind

```java
CotaniConfigs configs = CotaniConfigs.create(plugin)
    .file("config.yml")
    .load();

PluginSettings settings = configs.file("config.yml").bindOrThrow(PluginSettings.class);
```

### Async reload

```java
configs.reloadAsync()
    .thenRun(() -> this.settings = configs.file("config.yml").bindOrThrow(PluginSettings.class))
    .toCompletionStage();
```

## Anti-patterns

- Mutable config POJOs with setters.
- Reading raw `getConfig().getString(...)` scattered through the codebase.
- Synchronous file I/O on the main thread.

## Related skills

- `java-api-standards`
- `java-engineering-standards`

# cotani-config

Módulo de configuração moderno para plugins Paper. Suporta YAML, binding para `record`, validação, serializers extensíveis e reload assíncrono.

## Responsabilidade

- Ler e escrever arquivos YAML de forma tipada.
- Fazer binding automático de configurações para `record`s Java.
- Validar valores com anotações (`@Default`, `@Required`, `@Range`, etc.).
- Suportar reload assíncrono via `TaskChain`.
- Permitir serializers customizados para tipos de domínio.

## Stack

- Java 21+
- Paper API (Bukkit YAML)
- Adventure `Component` / MiniMessage
- JSpecify
- `cotani-core`, `cotani-task`, `cotani-text`

## Uso básico

```java
CotaniConfigs configs = CotaniConfigs.create(plugin)
    .file("config.yml")
    .file("messages.yml")
    .load();

PluginSettings settings = configs.file("config.yml")
    .bindOrThrow(PluginSettings.class);
```

## Record de config

```java
public record PluginSettings(
    @Default("false") boolean debug,
    @Default("5m") Duration autosave,
    MessageSettings messages
) {}

public record MessageSettings(
    @Default("<green>Cotani</green> <dark_gray>»</dark_gray>")
    Component prefix
) {}
```

## Reload assíncrono

```java
configs.reloadAsync()
    .thenRun(() -> {
        this.settings = configs.file("config.yml").bindOrThrow(PluginSettings.class);
        // atualize estado seguro
    })
    .toCompletionStage()
    .whenComplete((_, error) -> {
        if (error != null) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao recarregar config", error);
        }
    });
```

## Validação

```java
ValidationResult result = configs.file("config.yml").validate(PluginSettings.class);
if (!result.valid()) {
    for (ConfigIssue issue : result.issues()) {
        plugin.getLogger().warning(issue.path() + ": " + issue.message());
    }
}
```

## Anotações

| Anotação | Descrição |
|----------|-----------|
| `@Default("valor")` | Valor padrão quando a chave está ausente. |
| `@Required` | Campo obrigatório. |
| `@Range(min = 1, max = 6)` | Valida intervalo numérico. |
| `@ConfigPath("custom-path")` | Mapeia para um caminho YAML diferente. |
| `@ConfigType("MYSQL")` | Seleciona implementação de `sealed interface`. |

## Tipos suportados por padrão

- `String`, `int`, `long`, `double`, `float`, `boolean`
- `Duration`, `Path`, `UUID`
- `Component` (MiniMessage)
- `Material`, `Sound`, `NamespacedKey`, `Key`
- `Enum`
- `List<T>`, `Map<String, T>`
- `record`
- `sealed interface` com `@ConfigType`

## API pública

| Classe/Interface | Descrição |
|------------------|-----------|
| `CotaniConfigs` | Fachada para gerenciar múltiplos arquivos de config. |
| `CotaniConfig` | Acesso a um arquivo YAML específico. |
| `CotaniConfigsBuilder` | Builder fluente. |
| `RecordConfigBinder` / `ConfigBinder` | Binding de records para config. |
| `ConfigSerializer` / `ConfigSerializerRegistry` | Serializers customizados. |
| `ConfigValue` / `ConfigSection` | Acesso dinâmico a valores e seções. |
| `ValidationResult` / `ConfigIssue` / `ConfigValidator` | Validação de configurações. |
| `ConfigException` / `ConfigValidationException` | Exceções. |

## Estrutura de pacotes

```text
com.cotani.config
├── CotaniConfigs.java
├── CotaniConfig.java
├── CotaniConfigsBuilder.java
├── impl
│   ├── DefaultCotaniConfigs.java
│   └── DefaultCotaniConfig.java
├── binder
│   ├── ConfigBinder.java
│   └── RecordConfigBinder.java
├── serializer
│   ├── ConfigSerializer.java
│   ├── ConfigSerializerRegistry.java
│   └── defaults
│       ├── ComponentSerializer.java
│       ├── DurationSerializer.java
│       ├── MaterialSerializer.java
│       └── ...
├── validation
│   ├── ConfigValidator.java
│   ├── ValidationResult.java
│   └── ConfigIssue.java
├── value
│   └── ConfigValue.java
├── section
│   └── ConfigSection.java
├── source
│   ├── ConfigSource.java
│   └── BukkitYamlConfigSource.java
├── annotation
│   ├── Default.java
│   ├── Required.java
│   ├── Range.java
│   ├── ConfigPath.java
│   └── ConfigType.java
└── exception
    ├── ConfigException.java
    └── ConfigValidationException.java
```

## Dependência Gradle

```kotlin
dependencies {
    api(project(":config"))
}
```

## Integração

- Usa `cotani-text` para parsing de `Component`.
- Usa `cotani-task` para reload assíncrono.

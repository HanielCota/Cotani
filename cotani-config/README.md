# cotani-config

Módulo de configuração moderno para plugins Paper.

## Objetivos

- YAML simples para plugins Minecraft.
- Binding para `record`.
- Configs imutáveis no uso.
- Reload assíncrono.
- Validação com erros claros.
- Serializers extensíveis.
- Integração com Adventure `Component` e MiniMessage.
- Código organizado por responsabilidade.
- Fluxo baseado em early return.

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
    @Default("false")
    boolean debug,

    @Default("5m")
    Duration autosave,

    MessageSettings messages
) {
}

public record MessageSettings(
    @Default("<green>Cotani</green> <dark_gray>»</dark_gray>")
    Component prefix
) {
}
```

## Reload assíncrono

```java
configs.reloadAsync()
    .thenEntity(player, unused -> {
        this.settings = configs.file("config.yml").bindOrThrow(PluginSettings.class);
        player.sendMessage(Component.text("Config recarregada.", NamedTextColor.GREEN));
    })
    .onFailureEntity(player, error -> {
        player.sendMessage(Component.text("Erro ao recarregar config.", NamedTextColor.RED));
    });
```

## Anotações

- `@Default("valor")`
- `@Required`
- `@Range(min = 1, max = 6)`
- `@ConfigPath("custom-path")`
- `@ConfigType("MYSQL")`

## Tipos suportados por padrão

- `String`
- `int`, `long`, `double`, `float`, `boolean`
- `Duration`
- `Path`
- `UUID`
- `Component`
- `Material`
- `Sound`
- `NamespacedKey`
- `Key`
- `Enum`
- `List<T>`
- `Map<String, T>`
- `record`
- `sealed interface` com `@ConfigType`

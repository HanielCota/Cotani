# cotani-config

YAML configuration mapping framework. Binds files to immutable Java `record`s with built-in validation, custom type serializers, and asynchronous reloads.

## Usage

```java
public record PluginConfig(
    @Default("true") boolean debug,
    @Range(min = 1, max = 60) int interval
) {}

CotaniConfigs configs = CotaniConfigs.create(plugin);
PluginConfig config = configs.file("config.yml").bindOrThrow(PluginConfig.class);
```

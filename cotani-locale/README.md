# cotani-locale

Locale preferences and localized message rendering for Cotani plugins.

## Features

- Immutable message catalogs with deterministic locale fallback (`pt-BR` → `pt` → configured default locale).
- Per-player locale preferences identified by `UUID`, without retaining live Bukkit objects.
- MiniMessage rendering through `cotani-text`.
- Safe text, component, number, date and plural/choice arguments through `MessageArguments`.
- Optional asynchronous persistence through the small `LocaleRepository` SPI.
- Configurable persistence timeout for load, save and delete operations.
- Adventure `Component` output that can be consumed by command, GUI, dialog and HUD modules.

## Usage

```java
var catalog = LocaleCatalog.builder(LocaleId.of("en-US"))
    .bundle(MessageBundle.of(LocaleId.of("en-US"), Map.of(
        "welcome", "<green>Hello <name>")))
    .bundle(MessageBundle.of(LocaleId.of("pt-BR"), Map.of(
        "welcome", "<green>Olá <name>")))
    .build();

LocaleService locales = CotaniLocales.inMemory(catalog);
locales.setPlayerLocaleAsync(playerId, LocaleId.of("pt-BR"))
    .thenAccept(_ -> {
        Component welcome = locales.render(
            playerId,
            MessageKey.of("welcome"),
            MessageArguments.builder().text("name", "Steve").build());
        // Send `welcome` through the command, GUI, dialog or HUD audience.
    });
```

When the service is repository-backed, register its asynchronous shutdown with
`Cotani.forPlugin(plugin).withAsync(locales::closeAsync)`.

Templates are trusted configuration. Dynamic values are bound as unparsed placeholders, so a player name containing
MiniMessage syntax remains literal text.

The module does not read YAML or own SQL tables. Load configured maps with `cotani-config`, then construct
`MessageBundle` values; use `LocaleRepository` when player preferences need persistence.

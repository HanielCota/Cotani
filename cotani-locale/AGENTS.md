# cotani-locale

## Scope

Locale preferences, immutable message catalogs, deterministic fallback and MiniMessage rendering.

## Hard rules

1. Keep locale resolution independent of Bukkit and live `Player` objects; use `UUID` for player preferences.
2. Keep message templates trusted configuration. Use `MessageArguments` and `Placeholders` for dynamic values.
3. Do not concatenate untrusted values into MiniMessage templates.
4. Use `CompletionStage` for repository-backed preference mutations and propagate persistence failures.
5. Register the service in `Cotani.forPlugin(plugin).withAsync(localeService::closeAsync)` when the plugin owns its
   lifecycle.

## Patterns

```java
var service = CotaniLocales.inMemory(
    LocaleCatalog.builder(LocaleId.of("en-US"))
        .bundle(MessageBundle.of(LocaleId.of("en-US"), Map.of("welcome", "<green>Hello <name>")))
        .build());

var message = service.render(
    playerId,
    MessageKey.of("welcome"),
    MessageArguments.builder().text("name", "Steve").build());
```

`LocaleService` returns Adventure `Component`s, so command, GUI, dialog and HUD modules can consume the same result
without a direct dependency on those modules.

## Anti-patterns

- Storing `Player` references in locale state.
- Calling `MiniMessage` directly with string concatenation for user-controlled values.
- Silently hiding missing messages or persistence failures.
- Blocking on repository futures in the module.

# cotani-text

## Scope

Text utilities based on Adventure: MiniMessage parsing, placeholders and audience messaging.

## Hard rules

1. Use `MiniMessages` for parsing MiniMessage strings; avoid creating multiple `MiniMessage` instances.
2. Use `Placeholders` factory methods for dynamic values instead of manual string replacement.
3. Serialize user-facing text to `Component`; only convert to legacy/plain/JSON when an external API requires it.
4. Never pass `null` to `MiniMessages.parse(...)`, `Placeholders.unparsed(...)`, etc.

## Patterns

### Parse with placeholders

```java
Component msg = MiniMessages.parse(
    "<green>Olá, <player>! Você tem <amount> coins.",
    Placeholders.unparsed("player", player.getName()),
    Placeholders.unparsed("amount", String.valueOf(balance)));
```

### Send to audience

```java
AudienceMessages.sendMessage(player, "<green>Saldo: <balance>",
    Placeholders.component("balance", Component.text("R$ 100")));
```

### Convert formats

```java
Component fromLegacy = ComponentTexts.fromLegacy("&aTexto verde");
String mini = ComponentTexts.toMiniMessage(component);
```

## Anti-patterns

- Concatenating strings and then parsing as MiniMessage.
- Using legacy `§` or `&` codes directly in config files; prefer MiniMessage.
- Sending raw strings instead of `Component` to players.

## Related skills

- `java-engineering-standards`

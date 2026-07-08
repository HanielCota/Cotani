# cotani-text

Text utilities based on Adventure: MiniMessage parsing, placeholders and audience messaging.

## Overview

`cotani-text` is a modern text-formatting library designed for Paper. Built on top of Kyori's Adventure API, it simplifies translating rich-text strings (using MiniMessage syntax) to chat, action-bars, and titles, providing fluent placeholder handling.

## Features

- **Kyori Adventure Integration**: Native support for modern chat component structures.
- **Fluent Placeholder System**: Declare dynamic variables with `Placeholders.unparsed` or `Placeholders.component` to avoid manual string replacements.
- **Format Translators**: Convert color codes to and from legacy (`&`/`§`), MiniMessage tags, and JSON formats.
- **Reflection-Free Auditing**: Decoupled message sending routines with target-audience safety.

## Usage

### 1. Parsing MiniMessage with Placeholders

Safely format configurations with dynamic placeholder substitutions:

```java
import com.cotani.text.MiniMessages;
import com.cotani.text.Placeholders;
import net.kyori.adventure.text.Component;

Component message = MiniMessages.parse(
    "<green>Olá, <player>! Seu saldo atual é <amount> coins.",
    Placeholders.unparsed("player", player.getName()),
    Placeholders.unparsed("amount", String.valueOf(balance))
);
```

### 2. Sending Messages directly to Audiences

Send formatted templates to players or console:

```java
import com.cotani.text.AudienceMessages;
import com.cotani.text.Placeholders;

AudienceMessages.sendMessage(player, "<green>Saldo: <balance>",
    Placeholders.component("balance", Component.text("R$ 100"))
);
```

### 3. Converting Formats

Convert legacy formatting to Adventure components:

```java
import com.cotani.text.ComponentTexts;
import net.kyori.adventure.text.Component;

Component fromLegacy = ComponentTexts.fromLegacy("&aGreen text");
String mini = ComponentTexts.toMiniMessage(fromLegacy); // Returns "<green>Green text"
```

## Hard Rules & Best Practices

1. **Centralized Parsing**: Use `MiniMessages` helper methods to parse strings. Do not instantiate custom, private `MiniMessage` objects repeatedly.
2. **Structural Placeholders**: Always use the `Placeholders` factory to bind dynamic data. Never run manual `.replace()` or string concatenations prior to parsing templates.
3. **Adventure Native**: Build and maintain user-facing text inside `Component` classes. Only translate to plain text or legacy JSON formats when external libraries demand specific types.
4. **Strict Null-Safety**: Never pass `null` values into `MiniMessages.parse()`, `Placeholders.unparsed()`, or other utility signatures.

## Anti-Patterns

- Concatenating variables in code and then passing the combined string into MiniMessage parser (opens injection vulnerabilities if user inputs contain tag brackets).
- Hardcoding legacy `§` or `&` color indicators inside configuration setups. Prefer modern MiniMessage color nodes.
- Dispatched raw, unformatted `String` payloads directly to game audiences.

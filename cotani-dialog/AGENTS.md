# cotani-dialog

## Scope

Non-blocking, reactive chat, sign, and anvil input prompts and multi-step conversation wizards for Paper and Folia.

## Hard rules

1. Register `CotaniDialogs.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Always handle prompt results asynchronously through `CompletionStage` without calling `join()` or blocking the main thread.
3. Use `PromptResult` matching (`ifSuccess`, `ifCancelled`, `ifTimeout`) to react to player inputs safely.
4. Always escape player input before interpolating into output chat or MiniMessage components.

## Patterns

### Prompting Player Input via Chat

```java
var prompt = CotaniDialogs.chat()
    .prompt(MiniMessages.parse("<yellow>Please enter your confirmation code:</yellow>"))
    .parser(input -> input.matches("\\d{6}") ? Optional.of(input) : Optional.empty())
    .timeout(Duration.ofSeconds(30))
    .build();

prompt.start(player).thenAccept(result -> {
    result.ifSuccess(code -> {
        player.sendMessage(MiniMessages.parse("<green>Code accepted!</green>"));
    });
    result.ifTimeout(() -> {
        player.sendMessage(MiniMessages.parse("<red>Prompt timed out.</red>"));
    });
});
```

## Anti-patterns

- Blocking the main thread while waiting for a player to type in chat.
- Capturing live Bukkit/Paper objects in long-lived conversation contexts across server restarts.

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`
- `java-engineering-standards`

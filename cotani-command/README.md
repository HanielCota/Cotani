# cotani-command

Declarative, async-first, and Folia-safe command framework for Paper.

## Features

- **Fluent Tree API**: Build commands, nested subcommands, and arguments with a type-safe fluent builder.
- **Folia & Paper Thread-Safety**: Explicit execution target models (`executes`, `executesAsync`, `executesEntity`).
- **Rich Typed Arguments**: Built-in parsers for strings (unquoted, quoted, greedy), numbers, BigDecimals, booleans, durations, UUIDs, players, choices, and enums.
- **Async & Reactive Tab Completion**: Automatic context-aware suggestions for subcommands and argument choices with duplicate prevention.
- **Built-in Cooldowns & Permissions**: Declarative permission requirements and in-memory or distributed cooldown policies.
- **Adventure & MiniMessage Feedback**: Customizable user-facing error messages, usages, and feedback components with built-in internationalization (English and pt-BR).
- **Security & Tag-Injection Shield**: Automatic escaping of untrusted user input before compiling MiniMessage components.
- **Clean Lifecycle**: Seamless registration to Paper `CommandMap` with automated unregistration on plugin shutdown.

## Setup

Register the module once in `onEnable` via `Cotani.forPlugin`:

```java
PaperTaskScheduler scheduler = SchedulerFactory.create(this);
CotaniCommands commands = CotaniCommands.create(this, scheduler);

Cotani.forPlugin(this)
    .with(scheduler)
    .with(commands)
    .build();
```

## Example Usage

### 1. Simple Synchronous Command

```java
CommandNode pingCommand = CommandBuilder.of("ping")
    .aliases("p", "latency")
    .description("Check server latency")
    .permission("cotani.command.ping")
    .playerOnly()
    .cooldown(Duration.ofSeconds(3))
    .executes(ctx -> {
        Player player = ctx.requirePlayer();
        ctx.reply("<green>Pong! Latency: <yellow>" + player.getPing() + "ms</yellow>.</green>");
    })
    .build();

commands.register(pingCommand);
```

### 2. Subcommands with Lambda Configuration and Typed Getters

```java
commands.register("warp", root -> {
    root.description("Warp navigation system")
        .subcommand("create", create -> {
            create.permission("admin.warp.create")
                .argument(Arguments.string("name"))
                .executesEntity((ctx, player) -> {
                    String name = ctx.getString("name");
                    warpService.createWarp(name, player.getLocation());
                    ctx.replySuccess("Warp '<yellow>" + name + "</yellow>' created successfully!");
                });
        })
        .subcommand("tp", tp -> {
            tp.argument(Arguments.string("name"))
                .executesEntity((ctx, player) -> {
                    String name = ctx.getString("name");
                    warpService.teleport(player, name);
                });
        });
});
```

### 3. Asynchronous Pipeline with Typed Economy Arguments

```java
CommandNode payCommand = CommandBuilder.of("pay")
    .description("Transfer money to another player")
    .argument(Arguments.player("target"))
    .argument(Arguments.bigDecimal("amount", BigDecimal.ONE, new BigDecimal("1000000")))
    .executesAsync(ctx -> {
        Player sender = ctx.requirePlayer();
        Player target = ctx.getPlayer("target");
        BigDecimal amount = ctx.getBigDecimal("amount");

        return economyService.transferAsync(
            sender.getUniqueId(),
            target.getUniqueId(),
            amount,
            EconomyReason.custom("PLAYER_PAY")
        ).thenAccept(result -> {
            ctx.reply("<green>Successfully transferred <gold>$" + amount + "</gold> to <yellow>" + target.getName() + "</yellow>!</green>");
        });
    })
    .build();

commands.register(payCommand);
```

### 4. Custom Feedback & Localization (pt-BR)

```java
CotaniCommands commands = CotaniCommands.builder(plugin, scheduler)
    .feedback(CommandFeedback.ptBR())
    .build();
```

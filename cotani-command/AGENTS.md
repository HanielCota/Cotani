# cotani-command

## Scope

Declarative, async-first, and Folia-safe command framework for Paper.

## Hard rules

1. Register `CotaniCommands.create(plugin, scheduler)` once in `onEnable` via `Cotani.forPlugin(plugin).with(...)`.
2. Build commands with `CommandBuilder.of(...)` or `CotaniCommands.builder(...)`.
3. Choose explicit execution targets:
   - `executes(...)`: Fast, non-blocking synchronous logic on the main thread.
   - `executesAsync(...)`: Heavy I/O, storage queries, or async service orchestration returning `CompletionStage`.
   - `executesEntity(...)`: Player/region mutations (Paper main thread and Folia entity-region safe).
4. Never block threads inside handlers (`join()`, `get()`, `Thread.sleep(...)`).
5. Use typed arguments from `Arguments.*` (e.g., `Arguments.player(...)`, `Arguments.bigDecimal(...)`, `Arguments.quotedString(...)`).
6. All user inputs interpolated into MiniMessage templates must be escaped via `MiniMessages.escape(raw)`.

## Patterns

### 1. Simple Synchronous Command

`java
var pingCommand = CommandBuilder.of("ping")
    .aliases("p", "latency")
    .description("Check player latency")
    .playerOnly()
    .cooldown(Duration.ofSeconds(3))
    .executes(ctx -> {
        var player = ctx.requirePlayer();
        ctx.reply("<green>Pong! Latency: <yellow>" + player.getPing() + "ms</yellow>.</green>");
    })
    .build();

commands.register(pingCommand);
`

### 2. Async Command with Typed Arguments

`java
var payCommand = CommandBuilder.of("pay")
    .description("Transfer money to another player")
    .argument(Arguments.player("target"))
    .argument(Arguments.bigDecimal("amount", BigDecimal.ONE, new BigDecimal("1000000")))
    .executesAsync(ctx -> {
        // Arguments.player stores an immutable PlayerRef resolved on the main thread, so the
        // handler never touches live Player objects.
        var senderId = ctx.playerId().orElseThrow();
        var target = ctx.getPlayerRef("target");
        var amount = ctx.getBigDecimal("amount");

        return economyService.transferAsync(
            senderId,
            target.id(),
            amount,
            EconomyReason.custom("PLAYER_PAY")
        ).thenAccept(result -> {
            // ctx.reply is thread-safe: delivery is routed to the sender's owning thread.
            ctx.reply("<green>Transferred <gold>$" + amount + "</gold> to <yellow>" + MiniMessages.escape(target.name()) + "</yellow>!</green>");
        });
    })
    .build();

commands.register(payCommand);
`

### 3. Subcommand Tree with Lambda Configuration

`java
commands.register("warp", root -> {
    root.description("Warp navigation system")
        .subcommand("create", create -> {
            create.permission("admin.warp.create")
                .argument(Arguments.string("name"))
                .executesEntity((ctx, player) -> {
                    var name = ctx.getString("name");
                    warpService.createWarp(name, player.getLocation());
                    ctx.replySuccess("Warp '<yellow>" + name + "</yellow>' created!");
                });
        })
        .subcommand("tp", tp -> {
            tp.argument(Arguments.string("name"))
                .executesEntity((ctx, player) -> {
                    var name = ctx.getString("name");
                    warpService.teleport(player, name);
                });
        });
});
`

## Anti-patterns

- Blocking on `future.join()` or `future.get()` inside command handlers.
- Touching live Bukkit/Paper `Player` or `World` objects inside `executesAsync` without transitioning to entity/main thread; capture `ctx.playerId()` and immutable values instead.
- Calling `ctx.requirePlayer()` inside `executesAsync`; it is reserved for `executes`/`executesEntity` handlers.
- Manually parsing raw string tokens when typed `Arguments` are available.
- Unescaped interpolation of raw user input into MiniMessage formatting.

## Related skills

- `paper-plugin-architecture`
- `java-async-standards`
- `java-api-standards`
- `java-engineering-standards`

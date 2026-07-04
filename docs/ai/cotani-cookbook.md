:warning: **Agent-only reference.** This file contains copy-paste recipes for common Cotani usage patterns. Always cross-check generated code against the module `AGENTS.md` and the project skills.

---

# Cotani Cookbook

Recipes for the most common plugin scenarios when using Cotani modules.

---

## 1. Plugin bootstrap

Register all startup resources in `Cotani` and close them in order on shutdown.

```java
public final class MyPlugin extends JavaPlugin {

    private Cotani cotani;
    private PaperTaskScheduler scheduler;

    @Override
    public void onEnable() {
        scheduler = SchedulerFactory.create(this);

        cotani = Cotani.forPlugin(this)
            .with(scheduler)
            .build();
    }

    @Override
    public void onDisable() {
        cotani.close();
    }
}
```

---

## 2. Player data cache

```java
PlayerDataCache<User> users = CotaniCache.players(User.class)
    .repository(new UserRepository(scheduler))
    .defaultValue(User::createDefault)
    .preset(CachePreset.PLAYER_DATA)
    .build(this, scheduler);

users.updateAsync(player.getUniqueId(), user -> user.addCoins(100))
    .thenAccept(updated -> { /* updated is the new immutable value */ });
```

---

## 3. Config reload command

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage("Player only.");
        return true;
    }

    configs.reloadAsync()
        .thenRun(() -> this.settings = configs.file("config.yml").bindOrThrow(PluginSettings.class))
        .toCompletionStage()
        .whenComplete((_, error) -> {
            if (error != null) {
                player.sendMessage(Component.text("Reload failed.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Reloaded.", NamedTextColor.GREEN));
            }
        });

    return true;
}
```

---

## 4. Economy command

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
        return true;
    }

    EconomyOperationId operationId = EconomyOperationId.random();
    EconomyReason reason = EconomyReason.player("pay");

    economy.withdraw(player.getUniqueId(), BigDecimal.valueOf(50), reason, operationId)
        .whenComplete((transaction, error) -> {
            if (error instanceof InsufficientFundsException) {
                player.sendMessage(Component.text("Insufficient funds.", NamedTextColor.RED));
            } else if (error != null) {
                player.sendMessage(Component.text("Transaction failed.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Paid 50 coins.", NamedTextColor.GREEN));
            }
        });

    return true;
}
```

---

## 5. Teleport command

```java
@Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
        return true;
    }

    Location target = resolveTarget(args); // capture immutable data

    teleportModule.teleportService().teleport(
        TeleportRequest.builder()
            .playerId(player.getUniqueId())
            .target(target)
            .cause(TeleportCause.COMMAND)
            .source("mycommand")
            .options(TeleportOptions.defaults())
            .build()
    ).thenAccept(result -> switch (result) {
        case TeleportResult.Success success ->
            player.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
        case TeleportResult.Failure failure ->
            player.sendMessage(Component.text("Failed: " + failure.reason(), NamedTextColor.RED));
    });

    return true;
}
```

---

## 6. Storage repository

```java
public final class UserRepository extends CotaniRepository {

    public UserRepository(CotaniStorage storage) {
        super(storage);
    }

    public CompletionStage<Optional<User>> findByUuid(UUID uuid) {
        return table("users")
            .select()
            .where("uuid", uuid)
            .single()
            .thenApply(maybeRow -> maybeRow.map(this::map));
    }

    public CompletionStage<Void> save(User user) {
        return table("users")
            .upsert()
            .set("uuid", user.uuid())
            .set("username", user.username())
            .execute()
            .thenApply(_ -> null);
    }

    private User map(Row row) {
        return new User(
            row.getUuid("uuid"),
            row.getString("username")
        );
    }
}
```

---

## 7. Async-safe listener

```java
public final class PlayerKillListener implements Listener {

    private final EconomyService economy;

    public PlayerKillListener(EconomyService economy) {
        this.economy = economy;
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        UUID killerId = killer.getUniqueId();

        economy.deposit(
                killerId,
                BigDecimal.valueOf(10),
                EconomyReason.system("pvp_kill"),
                EconomyOperationId.random())
            .whenComplete((transaction, error) -> {
                if (error != null) {
                    // log; do not touch Paper API here unless through scheduler
                }
            });
    }
}
```

---

## 8. Audience message with placeholders

```java
AudienceMessages.sendMessage(player,
    "<green><player></green>, seu saldo é <yellow><balance></yellow>.",
    Placeholders.unparsed("player", player.getName()),
    Placeholders.unparsed("balance", balance.toPlainString()));
```

---

## 9. Item builder

```java
ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
    .customName("<red>Espada Lendária")
    .lore("<gray>Dano aumentado", "<dark_gray>Item raro")
    .enchant(Enchantment.SHARPNESS, 5)
    .glow()
    .build();

player.getInventory().addItem(item);
```

---

## 10. TaskChain async → main thread

```java
scheduler.supplyAsync(() -> heavyComputation(uuid))
    .thenGlobal(result -> {
        // safe to use Player/World/Entity here
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(Component.text("Result: " + result));
        }
        return result;
    })
    .thenAsync(result -> persistResult(uuid, result))
    .toCompletionStage();
```

---

## Checklist for every recipe

- [ ] No `join()`, `get()` or `Thread.sleep(...)` in application code.
- [ ] Live Bukkit/Paper objects (`Player`, `World`, etc.) are only touched on the main thread.
- [ ] Immutable IDs (`UUID`, value objects) are captured before async work.
- [ ] Async results are composed through `CompletionStage` or `TaskChain`.
- [ ] Domain exceptions are handled in `whenComplete` or `exceptionallyCompose`.
- [ ] Resources created at startup are registered in `Cotani`.

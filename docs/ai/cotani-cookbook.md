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
            .withAsync(scheduler::closeAsync)
            .build();
    }

    @Override
    public void onDisable() {
        if (cotani != null) {
            cotani.closeAsync().exceptionally(error -> {
                getLogger().log(java.util.logging.Level.SEVERE, "Could not close Cotani", error);
                return null;
            });
        }
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

    UUID playerId = player.getUniqueId();
    configs.reloadAsync()
        .thenAsync(_ -> configs.file("config.yml").bindOrThrow(PluginSettings.class))
        .consumeEntity(playerId, updated -> {
            this.settings = updated;
            Player current = Bukkit.getPlayer(playerId);
            if (current != null) {
                current.sendMessage(Component.text("Reloaded.", NamedTextColor.GREEN));
            }
        })
        .onError(error -> scheduler.entity(playerId, () -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current != null) {
                current.sendMessage(Component.text("Reload failed.", NamedTextColor.RED));
            }
        }))
        .toCompletionStage();

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
    UUID playerId = player.getUniqueId();
    EconomyReason reason = EconomyReason.player("pay", playerId);

    economy.withdrawAsync(playerId, BigDecimal.valueOf(50), reason, operationId)
        .whenComplete((transaction, error) -> {
            scheduler.entity(playerId, () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current == null) {
                    return;
                }
                Throwable cause = error instanceof java.util.concurrent.CompletionException wrapper
                    && wrapper.getCause() != null ? wrapper.getCause() : error;
                if (cause instanceof InsufficientFundsException) {
                    current.sendMessage(Component.text("Insufficient funds.", NamedTextColor.RED));
                } else if (error != null) {
                    current.sendMessage(Component.text("Transaction failed.", NamedTextColor.RED));
                } else {
                    current.sendMessage(Component.text("Paid 50 coins.", NamedTextColor.GREEN));
                }
            });
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

    UUID playerId = player.getUniqueId();
    Location target = resolveTarget(args).clone(); // create the request on the entity thread

    scheduler.chain(teleportModule.teleportService().teleportAsync(
        TeleportRequest.builder()
            .playerId(playerId)
            .target(target)
            .cause(TeleportCause.COMMAND)
            .source("mycommand")
            .options(TeleportOptions.defaults())
            .build()
    )).consumeEntity(playerId, result -> {
        Player current = Bukkit.getPlayer(playerId);
        if (current == null) {
            return;
        }
        switch (result) {
            case TeleportResult.Success success ->
                current.sendMessage(Component.text("Teleported!", NamedTextColor.GREEN));
            case TeleportResult.Failure failure ->
                current.sendMessage(Component.text("Failed: " + failure.reason(), NamedTextColor.RED));
        }
    }).toCompletionStage();

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
            row.getUuidOptional("uuid").orElseThrow(),
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

        economy.depositAsync(
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

## 11. Declarative GUI with pagination and reactive toggle

```java
public void openShopMenu(Player player, List<ItemStack> items) {
    Property<Integer> page = State.of(0);
    BoolProperty autoSell = State.of(false);

    GuiWindow.panel("<gold><bold>Shop Menu</bold></gold>")
        .structure(
            "# # # # # # # # #",
            "# I I I I I I I #",
            "# I I I I I I I #",
            "# < . . T . . > X"
        )
        .border(Material.BLACK_STAINED_GLASS_PANE)
        .paginated('I', page, items, item -> Button.of(
            _ -> item,
            ctx -> ctx.player().sendMessage(Component.text("Clicked item"))
        ))
        .bind('<', Buttons.previousPage(page))
        .bind('>', Buttons.nextPage(page))
        .bindToggle('T', autoSell, Material.LEVER, "<yellow>Auto Sell", enabled -> {
            player.sendMessage(Component.text("Auto Sell: " + enabled));
        })
        .bind('X', Buttons.close())
        .open(player);
}
```

---

## 12. Interactive input / deposit GUI (Trash Bin / Recycler)

```java
public void openRecyclerMenu(Player player) {
    GuiWindow.panel("<gold><bold>Item Recycler</bold></gold>")
        .structure(
            "# # # # # # # # #",
            "# I I I I I I I #",
            "# # # # C # # # #"
        )
        .border(Material.GRAY_STAINED_GLASS_PANE)
        .allowPlayerInteraction('I') // Players can place and remove items directly in 'I' slots
        .bind('C', Button.of(
            _ -> Items.item(Material.EMERALD, "<green>Reciclar Itens", "<gray>Clique para processar os itens depositados"),
            ctx -> {
                Inventory inv = ctx.view().getTopInventory();
                ctx.player().sendMessage(Component.text("Itens processados!"));
            }
        ))
        .open(player);
}
```

---

## Checklist for every recipe

- [ ] No `join()`, `get()` or `Thread.sleep(...)` in application code.
- [ ] Live Bukkit/Paper objects (`Player`, `World`, etc.) are only touched on the main thread.
- [ ] Immutable IDs (`UUID`, value objects) are captured before async work.
- [ ] Async results are composed through `CompletionStage` or `TaskChain`.
- [ ] Domain exceptions are handled in `whenComplete` or `exceptionallyCompose`.
- [ ] Resources created at startup are registered in `Cotani`.

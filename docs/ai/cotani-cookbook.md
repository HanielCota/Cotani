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

## 13. Interactive Display Entity Hologram

```java
public void spawnServerWelcomeHologram(Location location, DisplayModule displays) {
    displays.holograms().builder("spawn_welcome")
        .billboard(DisplayBillboard.CENTER)
        .lineSpacing(0.28)
        .addItemLine(new ItemStack(Material.NETHER_STAR), 1.2f)
        .addLine("<gold><bold>COTANI NETWORK</bold></gold>")
        .addLine("<gray>Welcome to the server!</gray>")
        .addLine("<yellow>Click to open the main menu</yellow>")
        .onClick((player, hologram, clickType) -> {
            player.sendMessage(Component.text("Opening main menu..."));
        })
        .spawnAsync(location)
        .thenAccept(hologram -> {
            // Spawned safely on the Folia/Paper region thread
        });
}
```

---

## 14. Declarative Command with Async Pipeline and Cooldown

```java
public void registerCommands(CotaniCommands commands, EconomyService economy) {
    CommandNode payCommand = CotaniCommands.builder("pay")
        .aliases("transfer", "pagar")
        .description("Transfer money to another player")
        .permission("cotani.command.pay")
        .playerOnly()
        .cooldown(Duration.ofSeconds(3))
        .argument(Arguments.player("target"))
        .argument(Arguments.bigDecimal("amount", BigDecimal.ONE, new BigDecimal("1000000")))
        .executesAsync(ctx -> {
            Player sender = ctx.requirePlayer();
            Player target = ctx.get("target", Player.class);
            BigDecimal amount = ctx.get("amount", BigDecimal.class);

            return economy.transferAsync(
                sender.getUniqueId(),
                target.getUniqueId(),
                amount,
                EconomyReason.custom("PAY_COMMAND")
            ).thenAccept(result -> {
                ctx.reply("<green>Transferred <gold>$" + amount + "</gold> to <yellow>" + target.getName() + "</yellow>!</green>");
            });
        })
        .build();

    commands.register(payCommand);
}
```

---

## 15. Redis Multi-Tier Distributed Cache Invalidation

Synchronize local Caffeine L1 cache across all server instances in a network using Redis Pub/Sub invalidation bus.

```java
public PlayerDataCache<User> setupDistributedCache(
        Plugin plugin,
        PaperTaskScheduler scheduler,
        CotaniRedis redis) {

    var invalidationBus = RedisCacheInvalidationBus.of(
            redis,
            ChannelId.of("cache:invalidation:users"),
            RedisCodec.uuid()
    );

    return CotaniCaches.players(User.class)
            .repository(RedisCacheRepository.ofPrefix(redis.store(), "cache:users", UserCodec.INSTANCE))
            .invalidationBus(invalidationBus)
            .defaultValue(User::createDefault)
            .preset(CachePreset.PLAYER_DATA)
            .build(plugin, scheduler);
}
```

---

## 16. Redis Distributed Global Network Cooldowns

```java
DistributedCooldownService networkCooldowns = new RedisDistributedCooldownService(redis);

// Check if player has cooldown for daily reward across any network server
var key = new CooldownKey(new UserCooldownTarget(player.getUniqueId()), new CooldownAction("reward:daily"));

networkCooldowns.checkAndStartAsync(key, Duration.ofHours(24))
    .thenAccept(result -> {
        if (result.denied()) {
            player.sendMessage(Component.text("Already claimed! Remaining: " + result.remaining().toHours() + "h"));
            return;
        }
        // Give daily reward
    });
```

---

## 17. Cross-Server Distributed Event Bus

```java
EventBus localBus = CotaniEvents.create(plugin);
EventBus networkEventBus = new RedisDistributedEventBus(localBus, redis, ChannelId.of("events:global"))
    .registerCodec(GlobalAnnouncementEvent.class, AnnouncementCodec.INSTANCE);

// Publish an event to the local server AND broadcast to the entire network
networkEventBus.publishAsync(new GlobalAnnouncementEvent("Maintenance starting in 10 minutes!"));
```

---

## 18. Interactive Chat & Anvil Dialog Prompts

```java
DialogService dialogs = CotaniDialogs.create(plugin, scheduler);

// 1. Chat Prompt with custom parser and cancellation keywords
dialogs.chat()
    .message("<yellow>Type the amount of coins to transfer (or 'cancel'):</yellow>")
    .timeout(Duration.ofSeconds(30))
    .parser(raw -> {
        try {
            int amount = Integer.parseInt(raw);
            return amount > 0 ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    })
    .onInvalidInput(Component.text("Please enter a valid positive number!"))
    .build(dialogs)
    .start(player)
    .thenAccept(result -> {
        result.ifSuccess(amount -> player.sendMessage(Component.text("Transferring " + amount + " coins...")));
        result.ifCancelled(reason -> player.sendMessage(Component.text("Transfer cancelled: " + reason)));
    });

// 2. Anvil GUI Prompt
dialogs.anvil()
    .title(Component.text("Enter Clan Name"))
    .initialText("MyClan")
    .timeout(Duration.ofMinutes(1))
    .build(dialogs)
    .open(player)
    .thenAccept(result -> {
        result.ifSuccess(name -> player.sendMessage(Component.text("Clan created: " + name)));
    });
```

---

## 19. Player Nametag Formatting & TabList Priority

```java
NametagModule nametags = CotaniNametags.create(plugin, scheduler);

// 1. Global Admin Tag with sorting priority 1 (appears at the top of TabList)
var adminTag = Nametag.builder()
    .priority(1)
    .prefix("<red><bold>[ADMIN]</bold></red> ")
    .suffix(" <gray>[Staff]</gray>")
    .color(NamedTextColor.RED)
    .visibility(NametagVisibility.ALWAYS)
    .collisionRule(CollisionRule.NEVER)
    .build();

nametags.apply(player, adminTag);

// 2. Dynamic Clan / Ally Tag Provider
nametags.registerProvider((viewer, target) -> {
    if (clanService.areAllies(viewer, target)) {
        return Optional.of(Nametag.builder()
            .prefix("<aqua>[Ally]</aqua> ")
            .color(NamedTextColor.AQUA)
            .friendlyFire(false)
            .build());
    }
    return Optional.empty(); // Falls back to global tag
});
```

---

## 20. Virtual Packet-Based NPCs (`cotani-npc`)

Create interactive, client-side virtual NPCs without entity tick lag:

```java
NpcModule npcs = CotaniNpcs.create(plugin, scheduler);

// 1. Create interactive Quest NPC with skin texture and look-at player
var questNpc = npcs.create(builder -> builder
    .location(spawnLocation)
    .name("<gold><bold>Quest Master</bold></gold>")
    .skin(textureValue, textureSignature)
    .lookAtPlayer(true)
    .equipment(NpcEquipment.builder()
        .helmet(new ItemStack(Material.GOLDEN_HELMET))
        .mainHand(new ItemStack(Material.ENCHANTED_BOOK))
        .build())
    .onInteract(event -> {
        var player = event.player();
        if (event.action() == NpcInteractEvent.Action.RIGHT_CLICK) {
            player.sendMessage(MiniMessages.parse("<yellow>Quest Master: Greetings, adventurer!</yellow>"));
        }
    }));
```

---

## Checklist for every recipe

- [ ] No `join()`, `get()` or `Thread.sleep(...)` in application code.
- [ ] Live Bukkit/Paper objects (`Player`, `World`, etc.) are only touched on the main thread.
- [ ] Immutable IDs (`UUID`, value objects) are captured before async work.
- [ ] Async results are composed through `CompletionStage` or `TaskChain`.
- [ ] Domain exceptions are handled in `whenComplete` or `exceptionallyCompose`.
- [ ] Resources created at startup are registered in `Cotani`.



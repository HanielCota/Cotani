package com.cotani.examples.showcase;

import com.cotani.Cotani;
import com.cotani.command.CotaniCommands;
import com.cotani.economy.EconomyBootstrap;
import com.cotani.gui.CotaniGuiModule;
import com.cotani.inventory.CotaniInventories;
import com.cotani.reward.CotaniRewards;
import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.ItemGrant;
import com.cotani.reward.api.RewardDefinition;
import com.cotani.reward.api.RewardId;
import com.cotani.reward.api.RewardSettlementService;
import com.cotani.reward.integration.CotaniRewardIntegrations;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.scheduler.SchedulerFactory;
import com.cotani.teleport.CotaniTeleports;
import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.PendingTeleportService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ShowcasePlugin extends JavaPlugin {
    private @Nullable Cotani lifecycle;
    private @Nullable EconomyBootstrap economyBootstrap;

    @Override
    public void onEnable() {
        PaperTaskScheduler scheduler = SchedulerFactory.create(this);
        var bootstrap = EconomyBootstrap.createDefault();
        this.economyBootstrap = bootstrap;
        var economyService = bootstrap.service();
        var inventoryModule = CotaniInventories.create(this, scheduler, new ShowcaseInventoryRepository());

        var rewards = CotaniRewards.inMemory();
        var dailyReward = new RewardDefinition(
                RewardId.of("daily"),
                Duration.ofHours(24),
                Duration.ofDays(2),
                7,
                List.of(new CurrencyGrant("coins", BigDecimal.valueOf(100)), new ItemGrant("minecraft:diamond", 1)));
        rewards.register(dailyReward);
        RewardSettlementService settlement = CotaniRewards.settlement(
                rewards,
                List.of(
                        CotaniRewardIntegrations.economy(economyService),
                        CotaniRewardIntegrations.vanillaInventory(this, inventoryModule.service())));

        // Demo-only: production plugins must supply real CombatAdapter and RegionProtectionAdapter
        // implementations. The noop adapters skip combat-tag and region protection checks.
        PendingTeleportService pendingTeleportService = CotaniTeleports.create(
                        this, CombatAdapter.noop(), RegionProtectionAdapter.noop(), scheduler)
                .pendingTeleportService();
        CotaniGuiModule guiModule = CotaniGuiModule.create(this);
        CotaniCommands commands = CotaniCommands.create(this, scheduler);
        com.cotani.dialog.api.DialogService dialogs = com.cotani.dialog.CotaniDialogs.create(this, scheduler);

        this.lifecycle = Cotani.forPlugin(this)
                .with(guiModule)
                .with(commands)
                .with(dialogs)
                .withAsync(inventoryModule::closeAsync)
                .withAsync(rewards::closeAsync)
                .withAsync(scheduler::closeAsync)
                .build();

        commands.register(
                "daily",
                command -> command.description("Resgata a recompensa diária")
                        .playerOnly()
                        .executesAsync(ctx -> {
                            var playerId = ctx.requirePlayer().getUniqueId();
                            return settlement
                                    .claimOrRecoverAsync(playerId, dailyReward.id())
                                    .thenAccept(
                                            claim -> ctx.reply("<green>Recompensa diária entregue! <gray>(sequência: "
                                                    + claim.streak() + ")"));
                        }));

        commands.register("cotanieco", eco -> {
            eco.description("Sistema de economia de exemplo")
                    .playerOnly()
                    .executesAsync(ctx -> {
                        var player = ctx.requirePlayer();
                        var playerId = player.getUniqueId();
                        return economyService.balance(playerId).thenAccept(balance -> {
                            ctx.reply("<green>Seu saldo atual é: <gold>"
                                    + balance.amount().toPlainString() + "</gold> <yellow>"
                                    + balance.currencyId().value() + "</yellow>");
                        });
                    })
                    .subcommand("daily", daily -> {
                        daily.playerOnly()
                                .cooldown(java.time.Duration.ofHours(24))
                                .executesAsync(ctx -> {
                                    var player = ctx.requirePlayer();
                                    var playerId = player.getUniqueId();
                                    var rewardAmount = java.math.BigDecimal.valueOf(100);
                                    var opId = com.cotani.economy.transaction.EconomyOperationId.random();
                                    var reason = com.cotani.economy.transaction.EconomyReason.system("daily_reward");
                                    return economyService
                                            .deposit(playerId, rewardAmount, reason, opId)
                                            .thenAccept(tx -> {
                                                ctx.reply("<green>Você resgatou com sucesso <gold>"
                                                        + tx.amount().toPlainString() + "</gold> moedas!");
                                            });
                                });
                    });
        });

        commands.register("cotanispawn", spawn -> {
            spawn.description("Teleporta para o spawn").playerOnly().executesEntity((ctx, player) -> {
                var spawnLocation = player.getWorld().getSpawnLocation();
                var options = com.cotani.teleport.api.TeleportOptions.builder()
                        .checkCombat(true)
                        .safeLocation(true)
                        .build();
                pendingTeleportService.schedule(
                        player.getUniqueId(),
                        spawnLocation,
                        java.time.Duration.ofSeconds(3),
                        options,
                        com.cotani.teleport.api.TeleportCause.SPAWN,
                        "spawn_command");
                ctx.reply("<gold>Teleportando para o spawn em <yellow>3s</yellow>. Não se mova nem receba dano!");
            });
        });

        commands.register("cotanirecycler", recycler -> {
            recycler.description("Abre o menu reciclador").playerOnly().executesEntity((ctx, player) -> {
                ShowcaseRecyclerGui.open(player);
            });
        });

        commands.register("cotaniprompt", prompt -> {
            prompt.description("Demonstração de diálogo interativo de chat")
                    .playerOnly()
                    .executesAsync(ctx -> {
                        var player = ctx.requirePlayer();
                        return dialogs.chat()
                                .message("<yellow>Digite uma mensagem no chat (ou 'sair'):</yellow>")
                                .timeout(java.time.Duration.ofSeconds(30))
                                .parser(java.util.Optional::of)
                                .build(dialogs)
                                .start(player)
                                .thenAccept(result -> {
                                    result.ifSuccess(text -> player.sendMessage(
                                            net.kyori.adventure.text.Component.text("Você digitou: " + text)));
                                    result.ifCancelled(reason -> player.sendMessage(
                                            net.kyori.adventure.text.Component.text("Cancelado: " + reason)));
                                });
                    });
        });
    }

    @Override
    public void onDisable() {
        if (economyBootstrap != null) {
            economyBootstrap.close();
        }

        var currentLifecycle = lifecycle;
        if (currentLifecycle == null) {
            return;
        }

        var _ = currentLifecycle.closeAsync().whenComplete((_, error) -> {
            if (error != null) {
                getLogger().log(Level.SEVERE, "Falha ao desativar recursos do Cotani", error);
            }
        });
    }
}

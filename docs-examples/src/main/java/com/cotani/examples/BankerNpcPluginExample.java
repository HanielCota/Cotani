package com.cotani.examples;

import com.cotani.Cotani;
import com.cotani.dialog.CotaniDialogs;
import com.cotani.dialog.api.DialogService;
import com.cotani.economy.EconomyService;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.gui.CotaniGuiModule;
import com.cotani.gui.api.GuiWindow;
import com.cotani.gui.button.Button;
import com.cotani.gui.state.State;
import com.cotani.item.ItemBuilder;
import com.cotani.npc.CotaniNpcs;
import com.cotani.npc.api.Npc;
import com.cotani.npc.api.NpcInteractEvent;
import com.cotani.npc.api.NpcModule;
import com.cotani.region.CotaniRegions;
import com.cotani.region.api.Region3D;
import com.cotani.region.api.RegionFlag;
import com.cotani.region.api.RegionModule;
import com.cotani.task.scheduler.SchedulerFactory;
import com.cotani.text.MiniMessages;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * End-to-end example demonstrating the orchestration of:
 * - {@link NpcModule} for virtual banker NPC
 * - {@link RegionModule} for bank district protection
 * - {@link DialogService} for reactive non-blocking deposit/withdrawal input
 * - {@link EconomyService} for atomic financial transactions
 * - {@link GuiWindow} for reactive GUI state representation
 */
public final class BankerNpcPluginExample extends JavaPlugin {

    private @org.jspecify.annotations.Nullable Cotani cotani;
    private @org.jspecify.annotations.Nullable NpcModule npcModule;
    private @org.jspecify.annotations.Nullable RegionModule regionModule;
    private @org.jspecify.annotations.Nullable DialogService dialogService;
    private @org.jspecify.annotations.Nullable EconomyService economyService;

    @Override
    public void onEnable() {
        var scheduler = SchedulerFactory.create(this);
        this.npcModule = CotaniNpcs.create(this, scheduler);
        this.regionModule = CotaniRegions.create(this, scheduler);
        this.dialogService = CotaniDialogs.create(this, scheduler);

        this.cotani = Cotani.forPlugin(this)
                .with(CotaniGuiModule.create(this))
                .with(npcModule)
                .with(regionModule)
                .with(dialogService)
                .build();
    }

    public void setEconomyService(EconomyService service) {
        this.economyService = Objects.requireNonNull(service, "service");
    }

    public void setupBank(Location bankLocation) {
        var world = bankLocation.getWorld();
        if (world == null || regionModule == null || npcModule == null) {
            return;
        }

        // 1. Protect Bank District using cotani-region
        var bankRegion = Region3D.builder("bank-district", world.getUID())
                .name("<gold><bold>First National Bank</bold></gold>")
                .bounds(
                        bankLocation.getBlockX() - 15,
                        bankLocation.getBlockY() - 5,
                        bankLocation.getBlockZ() - 15,
                        bankLocation.getBlockX() + 15,
                        bankLocation.getBlockY() + 10,
                        bankLocation.getBlockZ() + 15)
                .priority(100)
                .flag(RegionFlag.PVP, false)
                .flag(RegionFlag.BLOCK_BREAK, false)
                .flag(RegionFlag.BLOCK_PLACE, false)
                .greeting("<green>Welcome to the Bank District!</green>")
                .build();

        regionModule.registerRegion(bankRegion);

        // 2. Spawn Virtual Banker NPC using cotani-npc
        var bankerNpc = Npc.builder()
                .location(bankLocation)
                .name("<gold><bold>Banker Oliver</bold></gold>")
                .lookAtPlayer(true)
                .onInteract(event -> {
                    if (event.action() == NpcInteractEvent.Action.RIGHT_CLICK) {
                        openBankGui(event.player());
                    }
                })
                .build();

        npcModule.spawn(bankerNpc);
    }

    private void openBankGui(Player player) {
        if (economyService == null) {
            return;
        }

        var playerId = player.getUniqueId();
        var balanceState = State.of("Loading...");
        var currency = CurrencyId.of("coins");

        // Fetch balance asynchronously without blocking
        economyService.balanceAsync(playerId, currency).thenAccept(balance -> {
            balanceState.set("$" + balance.amount().toPlainString());
        });

        var borderItem = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
                .customName(Component.empty())
                .build();
        var balanceItem = ItemBuilder.of(Material.GOLD_INGOT)
                .customName(MiniMessages.parse("<yellow><bold>Current Balance</bold></yellow>"))
                .build();
        var depositItem = ItemBuilder.of(Material.EMERALD)
                .customName(MiniMessages.parse("<green><bold>Deposit Funds</bold></green>"))
                .lore(List.of(MiniMessages.parse("<gray>Click to enter deposit amount</gray>")))
                .build();

        GuiWindow.panel("<dark_gray>Bank Account</dark_gray>")
                .structure("#########", "# B . D #", "#########")
                .bind('#', borderItem)
                .bind('B', Button.item(balanceItem))
                .bind('D', Button.of(_ -> depositItem, click -> {
                    click.player().closeInventory();
                    promptDeposit(click.player());
                }))
                .open(player);
    }

    private void promptDeposit(Player player) {
        final var economy = economyService;
        final var dialog = dialogService;
        if (economy == null || dialog == null) {
            return;
        }

        var playerId = player.getUniqueId();
        var currency = CurrencyId.of("coins");

        final net.kyori.adventure.audience.Audience audience = player;

        // 3. Prompt for deposit amount non-blockingly using cotani-dialog
        dialog.promptChat(player, MiniMessages.parse("<yellow>Enter deposit amount (or 'cancel'):</yellow>"))
                .thenAccept(result -> {
                    result.ifSuccess(input -> {
                        if (input.equalsIgnoreCase("cancel")) {
                            audience.sendMessage(MiniMessages.parse("<red>Deposit cancelled.</red>"));
                            return;
                        }

                        try {
                            var amount = new BigDecimal(input);
                            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                                audience.sendMessage(MiniMessages.parse("<red>Amount must be positive!</red>"));
                                return;
                            }

                            // 4. Atomic deposit with cotani-economy
                            economy.deposit(
                                            playerId,
                                            currency,
                                            amount,
                                            EconomyReason.plugin("bank_deposit", "banking"),
                                            EconomyOperationId.random())
                                    .thenAccept(transaction -> {
                                        audience.sendMessage(
                                                MiniMessages.parse("<green>Deposit complete! Transaction ID: "
                                                        + transaction
                                                                .operationId()
                                                                .value() + "</green>"));
                                    });
                        } catch (NumberFormatException e) {
                            audience.sendMessage(MiniMessages.parse("<red>Invalid numeric amount!</red>"));
                        }
                    });
                });
    }

    @Override
    public void onDisable() {
        if (cotani != null) {
            cotani.closeAsync();
        }
    }
}

package com.cotani.dialog.impl;

import com.cotani.api.InternalApi;
import com.cotani.dialog.api.AnvilPrompt;
import com.cotani.dialog.api.CancelReason;
import com.cotani.dialog.api.PromptResult;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultAnvilPrompt implements AnvilPrompt, ActivePrompt {

    private final Component title;
    private final String initialText;
    private final Duration timeout;
    private final ItemStack leftItem;
    private final DefaultDialogService dialogService;
    private final PaperTaskScheduler scheduler;

    private final CompletableFuture<PromptResult<String>> future = new CompletableFuture<>();
    private final AtomicBoolean finished = new AtomicBoolean();

    private @Nullable UUID targetPlayerId;
    private @Nullable Inventory inventory;
    private @Nullable SchedulerTask timeoutTask;

    public DefaultAnvilPrompt(
            Component title,
            String initialText,
            Duration timeout,
            ItemStack leftItem,
            DefaultDialogService dialogService,
            PaperTaskScheduler scheduler) {
        this.title = Objects.requireNonNull(title, "title");
        this.initialText = Objects.requireNonNull(initialText, "initialText");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.leftItem = Objects.requireNonNull(leftItem, "leftItem");
        this.dialogService = Objects.requireNonNull(dialogService, "dialogService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public String initialText() {
        return initialText;
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public UUID playerId() {
        return Objects.requireNonNull(targetPlayerId, "targetPlayerId");
    }

    public @Nullable Inventory inventory() {
        return inventory;
    }

    @Override
    public CompletionStage<PromptResult<String>> open(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        this.targetPlayerId = playerId;

        dialogService.registerActivePrompt(this);

        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            scheduler.entity(player, () -> {
                this.inventory = Bukkit.createInventory(player, InventoryType.ANVIL, title);
                ItemStack item = leftItem.clone();
                if (!initialText.isEmpty()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Component.text(initialText));
                        item.setItemMeta(meta);
                    }
                }
                inventory.setItem(0, item);
                player.openInventory(inventory);
            });
        } else {
            this.inventory = Bukkit.createInventory(player, InventoryType.ANVIL, title);
            ItemStack item = leftItem.clone();
            if (!initialText.isEmpty()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text(initialText));
                    item.setItemMeta(meta);
                }
            }
            inventory.setItem(0, item);
            player.openInventory(inventory);
        }

        this.timeoutTask = scheduler.asyncLater(
                () -> {
                    cancel(CancelReason.TIMEOUT);
                    scheduler.entity(player, player::closeInventory);
                },
                timeout);

        return future.whenComplete((_, _) -> {
            cleanup();
            dialogService.unregisterActivePrompt(playerId, this);
        });
    }

    /**
     * Handles clicking the output slot (slot 2) in the Anvil.
     *
     * @param outputItem item in the output slot
     */
    public void handleOutputClick(@Nullable ItemStack outputItem) {
        if (finished.get()) {
            return;
        }

        String resultText = "";
        if (outputItem != null && outputItem.hasItemMeta()) {
            var meta = outputItem.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                var comp = meta.displayName();
                if (comp != null) {
                    resultText = PlainTextComponentSerializer.plainText().serialize(comp);
                }
            }
        }

        complete(PromptResult.success(resultText));
    }

    @Override
    public void cancel(CancelReason reason) {
        complete(PromptResult.cancelled(reason));
    }

    private void complete(PromptResult<String> result) {
        if (finished.compareAndSet(false, true)) {
            future.complete(result);
        }
    }

    private void cleanup() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }
}

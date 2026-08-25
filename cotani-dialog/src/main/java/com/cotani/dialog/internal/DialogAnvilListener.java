package com.cotani.dialog.internal;

import com.cotani.api.InternalApi;
import com.cotani.dialog.api.CancelReason;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

@InternalApi
public final class DialogAnvilListener implements Listener {

    private final DefaultDialogService dialogService;

    public DialogAnvilListener(DefaultDialogService dialogService) {
        this.dialogService = Objects.requireNonNull(dialogService, "dialogService");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        var activePrompt = dialogService.getActivePrompt(player.getUniqueId());
        if (!(activePrompt instanceof DefaultAnvilPrompt anvilPrompt)) {
            return;
        }

        var topInventory = event.getView().getTopInventory();
        if (!topInventory.equals(anvilPrompt.inventory())) {
            return;
        }

        // Anvil slot 2 in top inventory is raw slot 2
        if (event.getRawSlot() == 2) {
            event.setCancelled(true);
            ItemStack resultItem = event.getCurrentItem();
            anvilPrompt.handleOutputClick(resultItem);
            player.closeInventory();
            return;
        }

        // Cancel clicks inside anvil input slots or shift-clicks into anvil to prevent item exploits
        if (event.getRawSlot() == 0 || event.getRawSlot() == 1 || event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        var activePrompt = dialogService.getActivePrompt(player.getUniqueId());
        if (activePrompt instanceof DefaultAnvilPrompt anvilPrompt
                && event.getView().getTopInventory().equals(anvilPrompt.inventory())) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < 3) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        var activePrompt = dialogService.getActivePrompt(event.getPlayer().getUniqueId());
        if (activePrompt instanceof DefaultAnvilPrompt anvilPrompt
                && event.getInventory().equals(anvilPrompt.inventory())) {
            anvilPrompt.cancel(CancelReason.USER_CANCELLED);
        }
    }
}

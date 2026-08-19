package com.cotani.examples.showcase;

import com.cotani.gui.api.GuiWindow;
import com.cotani.gui.button.Button;
import com.cotani.gui.button.Buttons;
import com.cotani.item.ItemBuilder;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Demonstrates an interactive input menu where players can deposit items to be processed.
 */
@NullMarked
public final class ShowcaseRecyclerGui {
    private ShowcaseRecyclerGui() {}

    public static void open(Player player) {
        Objects.requireNonNull(player, "player");

        GuiWindow.panel("<gold><bold>Cotani Item Recycler</bold></gold>")
                .structure("# # # # # # # # #", "# I I I I I I I #", "# # # # C # # # X")
                .border(Material.GRAY_STAINED_GLASS_PANE)
                .allowPlayerInteraction('I')
                .bind(
                        'C',
                        Button.of(
                                _ -> ItemBuilder.of(Material.EMERALD)
                                        .customName("<green><bold>Reciclar Itens</bold></green>")
                                        .lore(
                                                "<gray>Clique para processar todos os itens",
                                                "<gray>depositados nos slots acima.")
                                        .glow()
                                        .build(),
                                ctx -> processDepositedItems(ctx.player())))
                .bind('X', Buttons.close())
                .open(player);
    }

    private static void processDepositedItems(Player player) {
        var topInventory = player.getOpenInventory().getTopInventory();
        var contents = topInventory.getContents();
        if (contents == null) {
            return;
        }

        int recycledCount = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null
                    || stack.getType().isAir()
                    || stack.getType() == Material.GRAY_STAINED_GLASS_PANE
                    || stack.getType() == Material.EMERALD) {
                continue;
            }

            recycledCount += stack.getAmount();
            topInventory.setItem(slot, null);
        }

        if (recycledCount == 0) {
            player.sendMessage(Component.text("§cNenhum item válido encontrado nos slots de depósito."));
            return;
        }

        player.sendMessage(Component.text("§aVocê reciclou com sucesso §e" + recycledCount + " §aitens!"));
    }
}

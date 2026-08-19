package com.cotani.gui.button;

import com.cotani.item.ItemBuilder;
import com.cotani.item.SkullBuilder;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Items {
    private static final ConcurrentHashMap<Material, ItemStack> borderPaneCache = new ConcurrentHashMap<>();

    private Items() {}

    public static ItemStack item(Material material, String title, String... lore) {
        Objects.requireNonNull(material, "Parameter 'material' must not be null");
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        Objects.requireNonNull(lore, "Parameter 'lore' must not be null");

        var builder = ItemBuilder.of(material).customName(title);

        if (lore.length > 0) {
            builder.lore(lore);
        }
        return builder.build();
    }

    public static ItemStack item(Material material, Component title, Component... lore) {
        Objects.requireNonNull(material, "Parameter 'material' must not be null");
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        Objects.requireNonNull(lore, "Parameter 'lore' must not be null");

        var builder = ItemBuilder.of(material).customName(title);

        if (lore.length > 0) {
            builder.lore(lore);
        }
        return builder.build();
    }

    public static ItemStack head(Player player, String title, String... lore) {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        Objects.requireNonNull(lore, "Parameter 'lore' must not be null");

        var builder = SkullBuilder.create().player(player).customName(title);

        if (lore.length > 0) {
            builder.lore(lore);
        }
        return builder.build();
    }

    public static ItemStack head(Player player, Component title, Component... lore) {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        Objects.requireNonNull(lore, "Parameter 'lore' must not be null");

        var builder = SkullBuilder.create().player(player).customName(title);

        if (lore.length > 0) {
            builder.lore(lore);
        }
        return builder.build();
    }

    public static ItemStack borderPane(Material material) {
        Objects.requireNonNull(material, "Parameter 'material' must not be null");

        return borderPaneCache
                .computeIfAbsent(
                        material,
                        m -> ItemBuilder.of(m).customName(Component.empty()).build())
                .clone();
    }
}

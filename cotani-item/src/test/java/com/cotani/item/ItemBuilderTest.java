package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.junit.jupiter.api.Test;

class ItemBuilderTest {

    @Test
    void isFinalClassWithExpectedApi() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(ItemBuilder.class.getModifiers()));
        assertEquals(
                "com.cotani.item.ItemStackBuilder<com.cotani.item.ItemBuilder>",
                ItemBuilder.class.getGenericSuperclass().toString());

        ItemBuilder.class.getMethod("of", Material.class);
        ItemBuilder.class.getMethod("amount", int.class);
        ItemBuilder.class.getMethod("customName", Component.class);
        ItemBuilder.class.getMethod("customName", String.class);
        ItemBuilder.class.getMethod("enchant", Enchantment.class, int.class);
        ItemBuilder.class.getMethod("flags", ItemFlag[].class);
        ItemBuilder.class.getMethod("unbreakable");
        ItemBuilder.class.getMethod("build");
    }
}

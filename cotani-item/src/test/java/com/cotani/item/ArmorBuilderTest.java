package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.junit.jupiter.api.Test;

class ArmorBuilderTest {

    @Test
    void isFinalClassWithExpectedApi() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(ArmorBuilder.class.getModifiers()));
        assertEquals(
                "com.cotani.item.ItemStackBuilder<com.cotani.item.ArmorBuilder>",
                ArmorBuilder.class.getGenericSuperclass().toString());

        ArmorBuilder.class.getMethod("of", Material.class);
        ArmorBuilder.class.getMethod("trim", ArmorTrim.class);
        ArmorBuilder.class.getMethod("trim", TrimMaterial.class, TrimPattern.class);
        ArmorBuilder.class.getMethod("removeTrim");
        ArmorBuilder.class.getMethod("customName", Component.class);
        ArmorBuilder.class.getMethod("customName", String.class);
        ArmorBuilder.class.getMethod("build");
    }
}

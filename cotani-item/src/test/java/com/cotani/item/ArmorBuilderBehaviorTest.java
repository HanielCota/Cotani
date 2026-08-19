package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@SuppressWarnings({"NullAway", "removal", "try"})
class ArmorBuilderBehaviorTest {
    private static void withArmorMaterials(CheckedBody body) {
        try (MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            ItemStack helmetStack = mock(ItemStack.class);
            when(ItemStack.of(Material.IRON_HELMET)).thenReturn(helmetStack);

            body.run(helmetStack);
        }
    }

    @Test
    void ofReturnsBuilderForArmorMaterial() {
        withArmorMaterials(helmetStack -> {
            ArmorBuilder builder = ArmorBuilder.of(Material.IRON_HELMET);

            assertNotNull(builder);
            assertSame(builder, builder.amount(2));
        });
    }

    @Test
    void ofRejectsNullMaterial() {
        withArmorMaterials(helmetStack -> {
            assertThrows(NullPointerException.class, () -> ArmorBuilder.of(null));
        });
    }

    @Test
    void ofRejectsNonArmorMaterial() {
        withArmorMaterials(helmetStack -> {
            IllegalArgumentException failure =
                    assertThrows(IllegalArgumentException.class, () -> ArmorBuilder.of(Material.DIAMOND));

            assertEquals("Material must be armor: DIAMOND", failure.getMessage());
        });
    }

    @Test
    void trimRejectsNullArguments() {
        withArmorMaterials(helmetStack -> {
            ArmorBuilder builder = ArmorBuilder.of(Material.IRON_HELMET);

            assertThrows(NullPointerException.class, () -> builder.trim((ArmorTrim) null));
            assertThrows(NullPointerException.class, () -> builder.trim(null, null));
        });
    }

    @Test
    void amountAndFlagsDelegateToUnderlyingStack() {
        withArmorMaterials(helmetStack -> {
            ArmorBuilder builder = ArmorBuilder.of(Material.IRON_HELMET);

            assertSame(builder, builder.amount(2));
            verify(helmetStack).setAmount(2);

            assertSame(builder, builder.flags(ItemFlag.HIDE_ENCHANTS));
            verify(helmetStack).addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
    }

    private interface CheckedBody {
        void run(ItemStack helmetStack);
    }
}

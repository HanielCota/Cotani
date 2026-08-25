package com.cotani.item;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.registry.set.RegistryKeySet;
import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.JukeboxSong;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@SuppressWarnings({"NullAway", "try"})
class ItemBuilderBehaviorTest {
    private static final Material MATERIAL = Material.DIAMOND;

    private static void withBuilder(BiConsumer<ItemBuilder, ItemStack> test) {
        try (MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            ItemStack stack = mock(ItemStack.class);
            when(ItemStack.of(MATERIAL)).thenReturn(stack);
            test.accept(ItemBuilder.of(MATERIAL), stack);
        }
    }

    @Test
    void ofRejectsNullMaterial() {
        assertThrows(NullPointerException.class, () -> ItemBuilder.of(null));
    }

    @Test
    void ofDelegatesToItemStackOf() {
        try (MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            when(ItemStack.of(MATERIAL)).thenReturn(mock(ItemStack.class));

            ItemBuilder.of(MATERIAL);

            items.verify(() -> ItemStack.of(MATERIAL));
        }
    }

    @Test
    void buildReturnsClonedItemEachCall() {
        try (MockedStatic<ItemStack> items = mockStatic(ItemStack.class)) {
            ItemStack stack = mock(ItemStack.class);
            ItemStack first = mock(ItemStack.class);
            ItemStack second = mock(ItemStack.class);
            when(ItemStack.of(MATERIAL)).thenReturn(stack);
            when(stack.clone()).thenReturn(first, second);

            ItemBuilder builder = ItemBuilder.of(MATERIAL);

            assertSame(first, builder.build());
            assertSame(second, builder.build());
            assertNotSame(first, second);
            verify(stack, org.mockito.Mockito.times(2)).clone();
        }
    }

    @Test
    void amountDelegatesToItemStackAndReturnsSelf() {
        withBuilder((builder, stack) -> {
            assertSame(builder, builder.amount(3));
            verify(stack).setAmount(3);
        });
    }

    @Test
    void flagsAndRemoveFlagsDelegateAndReturnSelf() {
        withBuilder((builder, stack) -> {
            assertSame(builder, builder.flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES));
            verify(stack).addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

            assertSame(builder, builder.removeFlags(ItemFlag.HIDE_ATTRIBUTES));
            verify(stack).removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
    }

    @Test
    void textInputsRejectNullArguments() {
        withBuilder((builder, stack) -> {
            assertThrows(NullPointerException.class, () -> builder.customName((String) null));
            assertThrows(NullPointerException.class, () -> builder.itemName((String) null));
            assertThrows(NullPointerException.class, () -> builder.addLore((String) null));
            assertThrows(NullPointerException.class, () -> builder.addLore((Component) null));
            assertThrows(NullPointerException.class, () -> builder.lore((Component[]) null));
            assertThrows(NullPointerException.class, () -> builder.lore((String[]) null));
        });
    }

    @Test
    void enchantmentInputsRejectNullArguments() {
        withBuilder((builder, stack) -> {
            assertThrows(NullPointerException.class, () -> builder.enchant(null, 1));
            assertThrows(NullPointerException.class, () -> builder.enchant(null));
            assertThrows(NullPointerException.class, () -> builder.removeEnchant(null));
            assertThrows(NullPointerException.class, () -> builder.storedEnchant(null, 1));
            assertThrows(NullPointerException.class, () -> builder.storedEnchant(null));
            assertThrows(NullPointerException.class, () -> builder.flags((ItemFlag[]) null));
            assertThrows(NullPointerException.class, () -> builder.removeFlags((ItemFlag[]) null));
        });
    }

    @Test
    void dataComponentInputsRejectNullArguments() {
        withBuilder((builder, stack) -> {
            assertThrows(NullPointerException.class, () -> builder.customModelData(null));
            assertThrows(NullPointerException.class, () -> builder.customModelDataFloats((float[]) null));
            assertThrows(NullPointerException.class, () -> builder.customModelDataFlags((boolean[]) null));
            assertThrows(NullPointerException.class, () -> builder.customModelDataStrings((String[]) null));
            assertThrows(NullPointerException.class, () -> builder.customModelDataColors((Color[]) null));
            assertThrows(NullPointerException.class, () -> builder.itemModel((Key) null));
            assertThrows(NullPointerException.class, () -> builder.rarity(null));
            assertThrows(NullPointerException.class, () -> builder.hideAdditionalTooltip((DataComponentType[]) null));
            assertThrows(NullPointerException.class, () -> builder.resetData(null));
            assertThrows(NullPointerException.class, () -> builder.unsetData(null));
            assertThrows(NullPointerException.class, () -> builder.hasData(null));
        });
    }

    @Test
    void attributeInputsRejectNullArguments() {
        withBuilder((builder, stack) -> {
            assertThrows(NullPointerException.class, () -> builder.attribute(null, null));
            assertThrows(NullPointerException.class, () -> builder.persistentData(null));
        });
    }

    @Test
    void functionalBuildersRejectNullConsumers() {
        withBuilder((builder, stack) -> {
            assertThrows(NullPointerException.class, () -> builder.food(null));
            assertThrows(NullPointerException.class, () -> builder.tool(null));
            assertThrows(NullPointerException.class, () -> builder.weapon(null));
            assertThrows(NullPointerException.class, () -> builder.blocksAttacks(null));
            assertThrows(NullPointerException.class, () -> builder.consumable(null));
            assertThrows(
                    NullPointerException.class,
                    () -> builder.equippable((java.util.function.Consumer<Equippable.Builder>) null));
        });
    }

    @Test
    void materialAndStackInputsRejectNullArguments() {
        withBuilder((builder, stack) -> {
            assertThrows(NullPointerException.class, () -> builder.useRemainder((Material) null));
            assertThrows(NullPointerException.class, () -> builder.useRemainder((ItemStack) null));
            assertThrows(NullPointerException.class, () -> builder.repairable((ItemType[]) null));
            assertThrows(NullPointerException.class, () -> builder.repairable((List<ItemType>) null));
            assertThrows(NullPointerException.class, () -> builder.damageResistant((RegistryKeySet<DamageType>) null));
            assertThrows(NullPointerException.class, () -> builder.damageResistant(null));
            assertThrows(NullPointerException.class, () -> builder.equippable((EquipmentSlot) null));
        });
    }

    @Test
    void jukeboxAndPotionInputsRejectNullArguments() {
        withBuilder((builder, stack) -> {
            assertThrows(NullPointerException.class, () -> builder.jukeboxPlayable((JukeboxSong) null));
            assertThrows(NullPointerException.class, () -> builder.jukeboxPlayable((NamespacedKey) null));
            assertThrows(NullPointerException.class, () -> builder.potion(null));
            assertThrows(NullPointerException.class, () -> builder.potionEffects((PotionEffect[]) null));
            assertThrows(NullPointerException.class, () -> builder.dye(null));
        });
    }

    @Test
    void useCooldownRejectsInvalidKey() {
        withBuilder((builder, stack) -> {
            assertThrows(InvalidKeyException.class, () -> builder.useCooldown(1.0f, Key.key("minecraft", "bad key")));
        });
    }
}

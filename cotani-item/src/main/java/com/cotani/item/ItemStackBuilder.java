package com.cotani.item;

import com.cotani.text.MiniMessages;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.*;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import io.papermc.paper.registry.tag.Tag;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.JukeboxSong;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public abstract class ItemStackBuilder<T extends ItemStackBuilder<T>> {

    private final ItemStack item;

    protected ItemStackBuilder(Material material) {
        this.item = ItemStack.of(material);
    }

    protected abstract T self();

    protected final ItemStack item() {
        return item;
    }

    public final ItemStack build() {
        return item.clone();
    }

    public final T amount(int amount) {
        item.setAmount(amount);
        return self();
    }

    public final T customName(@Nullable Component name) {
        if (name == null) {
            item.unsetData(DataComponentTypes.CUSTOM_NAME);
            return self();
        }
        item.setData(DataComponentTypes.CUSTOM_NAME, name);
        return self();
    }

    public final T customName(String miniMessage) {
        Objects.requireNonNull(miniMessage, "Parameter 'miniMessage' must not be null");
        return customName(MiniMessages.parse(miniMessage));
    }

    public final T removeCustomName() {
        item.unsetData(DataComponentTypes.CUSTOM_NAME);
        return self();
    }

    public final T itemName(@Nullable Component name) {
        if (name == null) {
            item.unsetData(DataComponentTypes.ITEM_NAME);
            return self();
        }
        item.setData(DataComponentTypes.ITEM_NAME, name);
        return self();
    }

    public final T itemName(String miniMessage) {
        Objects.requireNonNull(miniMessage, "Parameter 'miniMessage' must not be null");
        return itemName(MiniMessages.parse(miniMessage));
    }

    public final T removeItemName() {
        item.unsetData(DataComponentTypes.ITEM_NAME);
        return self();
    }

    public final T lore(@Nullable List<Component> lines) {
        if (lines == null || lines.isEmpty()) {
            item.unsetData(DataComponentTypes.LORE);
            return self();
        }
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        return self();
    }

    public final T lore(Component... lines) {
        Objects.requireNonNull(lines, "Parameter 'lines' must not be null");
        if (lines.length == 0) {
            return clearLore();
        }
        return lore(Arrays.asList(lines));
    }

    public final T lore(String... miniMessages) {
        Objects.requireNonNull(miniMessages, "Parameter 'miniMessages' must not be null");
        var lines = Arrays.stream(miniMessages).map(MiniMessages::parse).toList();
        return lore(lines);
    }

    public final T addLore(Component line) {
        Objects.requireNonNull(line, "Parameter 'line' must not be null");
        var existing = item.getData(DataComponentTypes.LORE);
        var builder = ItemLore.lore();
        if (existing != null) {
            builder.lines(existing.lines());
        }
        builder.addLine(line);
        item.setData(DataComponentTypes.LORE, builder.build());
        return self();
    }

    public final T addLore(String miniMessage) {
        Objects.requireNonNull(miniMessage, "Parameter 'miniMessage' must not be null");
        return addLore(MiniMessages.parse(miniMessage));
    }

    public final T clearLore() {
        item.unsetData(DataComponentTypes.LORE);
        return self();
    }

    public final T enchant(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "Parameter 'enchantment' must not be null");
        return setEnchantments(DataComponentTypes.ENCHANTMENTS, enchantment, level);
    }

    public final T enchant(Map<Enchantment, Integer> enchantments) {
        Objects.requireNonNull(enchantments, "Parameter 'enchantments' must not be null");
        item.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(enchantments));
        return self();
    }

    public final T enchantUnsafe(Enchantment enchantment, int level) {
        return enchant(enchantment, level);
    }

    public final T removeEnchant(Enchantment enchantment) {
        Objects.requireNonNull(enchantment, "Parameter 'enchantment' must not be null");
        var existing = item.getData(DataComponentTypes.ENCHANTMENTS);
        if (existing == null || !existing.enchantments().containsKey(enchantment)) {
            return self();
        }
        var filtered = existing.enchantments().entrySet().stream()
                .filter(e -> !e.getKey().equals(enchantment))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        item.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(filtered));
        return self();
    }

    public final T clearEnchantments() {
        item.unsetData(DataComponentTypes.ENCHANTMENTS);
        return self();
    }

    public final T storedEnchant(Enchantment enchantment, int level) {
        Objects.requireNonNull(enchantment, "Parameter 'enchantment' must not be null");
        return setEnchantments(DataComponentTypes.STORED_ENCHANTMENTS, enchantment, level);
    }

    public final T storedEnchant(Map<Enchantment, Integer> enchantments) {
        Objects.requireNonNull(enchantments, "Parameter 'enchantments' must not be null");
        item.setData(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantments.itemEnchantments(enchantments));
        return self();
    }

    private T setEnchantments(DataComponentType.Valued<ItemEnchantments> type, Enchantment enchantment, int level) {
        var existing = item.getData(type);
        var builder = ItemEnchantments.itemEnchantments();
        if (existing != null) {
            existing.enchantments().forEach(builder::add);
        }
        builder.add(enchantment, level);
        item.setData(type, builder.build());
        return self();
    }

    public final T flags(ItemFlag... flags) {
        Objects.requireNonNull(flags, "Parameter 'flags' must not be null");
        item.addItemFlags(flags);
        return self();
    }

    public final T removeFlags(ItemFlag... flags) {
        Objects.requireNonNull(flags, "Parameter 'flags' must not be null");
        item.removeItemFlags(flags);
        return self();
    }

    public final T unbreakable() {
        item.setData(DataComponentTypes.UNBREAKABLE);
        return self();
    }

    public final T breakable() {
        item.unsetData(DataComponentTypes.UNBREAKABLE);
        return self();
    }

    public final T customModelData(int data) {
        return customModelDataFloats((float) data);
    }

    public final T customModelData(Consumer<CustomModelData.Builder> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        var builder = CustomModelData.customModelData();
        consumer.accept(builder);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
        return self();
    }

    public final T customModelDataFloats(float... floats) {
        Objects.requireNonNull(floats, "Parameter 'floats' must not be null");
        return customModelData(component -> component.addFloats(boxedFloats(floats)));
    }

    public final T customModelDataFlags(boolean... flags) {
        Objects.requireNonNull(flags, "Parameter 'flags' must not be null");
        return customModelData(component -> component.addFlags(boxedBooleans(flags)));
    }

    public final T customModelDataStrings(String... strings) {
        Objects.requireNonNull(strings, "Parameter 'strings' must not be null");
        return customModelData(component -> component.addStrings(List.of(strings)));
    }

    public final T customModelDataColors(Color... colors) {
        Objects.requireNonNull(colors, "Parameter 'colors' must not be null");
        return customModelData(component -> component.addColors(List.of(colors)));
    }

    public final T itemModel(NamespacedKey key) {
        item.setData(DataComponentTypes.ITEM_MODEL, key);
        return self();
    }

    public final T itemModel(Key key) {
        Objects.requireNonNull(key, "Parameter 'key' must not be null");
        var namespaced = NamespacedKey.fromString(key.asString());
        Objects.requireNonNull(namespaced, "Could not convert key to NamespacedKey: " + key);
        return itemModel(namespaced);
    }

    public final T removeItemModel() {
        item.unsetData(DataComponentTypes.ITEM_MODEL);
        return self();
    }

    public final T rarity(ItemRarity rarity) {
        Objects.requireNonNull(rarity, "Parameter 'rarity' must not be null");
        item.setData(DataComponentTypes.RARITY, rarity);
        return self();
    }

    public final T glintOverride(boolean glint) {
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, glint);
        return self();
    }

    public final T enchantmentGlintOverride(boolean glint) {
        return glintOverride(glint);
    }

    public final T glow() {
        return glintOverride(true);
    }

    public final T glow(boolean glow) {
        return glintOverride(glow);
    }

    public final T hideTooltip() {
        var tooltip = TooltipDisplay.tooltipDisplay().hideTooltip(true).build();
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, tooltip);
        return self();
    }

    public final T showTooltip() {
        item.unsetData(DataComponentTypes.TOOLTIP_DISPLAY);
        return self();
    }

    public final T hideAdditionalTooltip() {
        return hideAdditionalTooltip(
                DataComponentTypes.DYED_COLOR,
                DataComponentTypes.TRIM,
                DataComponentTypes.STORED_ENCHANTMENTS,
                DataComponentTypes.POTION_CONTENTS,
                DataComponentTypes.ENCHANTMENTS,
                DataComponentTypes.ATTRIBUTE_MODIFIERS,
                DataComponentTypes.CAN_BREAK,
                DataComponentTypes.CAN_PLACE_ON,
                DataComponentTypes.JUKEBOX_PLAYABLE);
    }

    public final T hideAdditionalTooltip(DataComponentType... components) {
        Objects.requireNonNull(components, "Parameter 'components' must not be null");
        var builder = TooltipDisplay.tooltipDisplay();
        builder.addHiddenComponents(components);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, builder.build());
        return self();
    }

    public final T tooltipStyle(NamespacedKey key) {
        item.setData(DataComponentTypes.TOOLTIP_STYLE, key);
        return self();
    }

    public final T tooltipStyle(Key key) {
        Objects.requireNonNull(key, "Parameter 'key' must not be null");
        var namespaced = NamespacedKey.fromString(key.asString());
        Objects.requireNonNull(namespaced, "Could not convert key to NamespacedKey: " + key);
        return tooltipStyle(namespaced);
    }

    public final T attribute(Attribute attribute, AttributeModifier modifier) {
        Objects.requireNonNull(attribute, "Parameter 'attribute' must not be null");
        Objects.requireNonNull(modifier, "Parameter 'modifier' must not be null");
        return attribute(attribute, modifier, modifier.getSlotGroup());
    }

    public final T attribute(Attribute attribute, AttributeModifier modifier, EquipmentSlotGroup slotGroup) {
        Objects.requireNonNull(attribute, "Parameter 'attribute' must not be null");
        Objects.requireNonNull(modifier, "Parameter 'modifier' must not be null");
        Objects.requireNonNull(slotGroup, "Parameter 'slotGroup' must not be null");
        var existing = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        var builder = ItemAttributeModifiers.itemAttributes();
        if (existing != null) {
            existing.modifiers()
                    .forEach(entry -> builder.addModifier(entry.attribute(), entry.modifier(), entry.getGroup()));
        }
        builder.addModifier(attribute, modifier, slotGroup);
        item.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build());
        return self();
    }

    public final T clearAttributes() {
        item.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return self();
    }

    public final T damage(int damage) {
        item.setData(DataComponentTypes.DAMAGE, damage);
        return self();
    }

    public final T maxDamage(int maxDamage) {
        item.setData(DataComponentTypes.MAX_DAMAGE, maxDamage);
        return self();
    }

    public final T repairCost(int cost) {
        item.setData(DataComponentTypes.REPAIR_COST, cost);
        return self();
    }

    public final T maxStackSize(int size) {
        item.setData(DataComponentTypes.MAX_STACK_SIZE, size);
        return self();
    }

    public final T persistentData(Consumer<PersistentDataContainer> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        item.editPersistentDataContainer(consumer);
        return self();
    }

    public final T food(int nutrition, float saturation) {
        return food(food -> food.nutrition(nutrition).saturation(saturation));
    }

    public final T food(Consumer<FoodProperties.Builder> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        var builder = FoodProperties.food();
        consumer.accept(builder);
        item.setData(DataComponentTypes.FOOD, builder.build());
        return self();
    }

    public final T alwaysEdible() {
        var existing = item.getData(DataComponentTypes.FOOD);
        var builder = existing == null
                ? FoodProperties.food()
                : FoodProperties.food().nutrition(existing.nutrition()).saturation(existing.saturation());
        builder.canAlwaysEat(true);
        item.setData(DataComponentTypes.FOOD, builder.build());
        return self();
    }

    public final T tool(Consumer<Tool.Builder> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        var builder = Tool.tool();
        consumer.accept(builder);
        item.setData(DataComponentTypes.TOOL, builder.build());
        return self();
    }

    public final T removeTool() {
        item.unsetData(DataComponentTypes.TOOL);
        return self();
    }

    public final T weapon(Consumer<Weapon.Builder> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        var builder = Weapon.weapon();
        consumer.accept(builder);
        item.setData(DataComponentTypes.WEAPON, builder.build());
        return self();
    }

    public final T useCooldown(float seconds) {
        return useCooldown(seconds, null);
    }

    public final T useCooldown(float seconds, @Nullable NamespacedKey group) {
        var builder = UseCooldown.useCooldown(seconds);
        if (group != null) {
            builder.cooldownGroup(group);
        }
        item.setData(DataComponentTypes.USE_COOLDOWN, builder.build());
        return self();
    }

    public final T useCooldown(float seconds, @Nullable Key group) {
        return useCooldown(seconds, group == null ? null : NamespacedKey.fromString(group.asString()));
    }

    public final T useRemainder(Material material) {
        return useRemainder(material, 1);
    }

    public final T useRemainder(Material material, int amount) {
        Objects.requireNonNull(material, "Parameter 'material' must not be null");
        var remainder = ItemStack.of(material, amount);
        item.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(remainder));
        return self();
    }

    public final T useRemainder(ItemStack stack) {
        Objects.requireNonNull(stack, "Parameter 'stack' must not be null");
        item.setData(DataComponentTypes.USE_REMAINDER, UseRemainder.useRemainder(stack));
        return self();
    }

    public final T enchantable(int value) {
        item.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(value));
        return self();
    }

    public final T repairable(ItemType... types) {
        Objects.requireNonNull(types, "Parameter 'types' must not be null");
        return repairable(List.of(types));
    }

    public final T repairable(List<ItemType> types) {
        Objects.requireNonNull(types, "Parameter 'types' must not be null");
        var repairableTypes = RegistrySet.keySetFromValues(RegistryKey.ITEM, types);
        item.setData(DataComponentTypes.REPAIRABLE, Repairable.repairable(repairableTypes));
        return self();
    }

    public final T glider() {
        item.setData(DataComponentTypes.GLIDER);
        return self();
    }

    public final T damageResistant(RegistryKeySet<DamageType> types) {
        Objects.requireNonNull(types, "Parameter 'types' must not be null");
        item.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(types));
        return self();
    }

    public final T damageResistant(Tag<DamageType> tag) {
        Objects.requireNonNull(tag, "Parameter 'tag' must not be null");
        return damageResistant((RegistryKeySet<DamageType>) tag);
    }

    public final T equippable(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "Parameter 'slot' must not be null");
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).build());
        return self();
    }

    public final T equippable(Consumer<Equippable.Builder> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        var existing = item.getData(DataComponentTypes.EQUIPPABLE);
        var builder = existing == null ? Equippable.equippable(EquipmentSlot.HEAD) : existing.toBuilder();
        consumer.accept(builder);
        item.setData(DataComponentTypes.EQUIPPABLE, builder.build());
        return self();
    }

    public final T jukeboxPlayable(JukeboxSong song) {
        Objects.requireNonNull(song, "Parameter 'song' must not be null");
        var playable = JukeboxPlayable.jukeboxPlayable(song).build();
        item.setData(DataComponentTypes.JUKEBOX_PLAYABLE, playable);
        return self();
    }

    public final T jukeboxPlayable(NamespacedKey songKey) {
        Objects.requireNonNull(songKey, "Parameter 'songKey' must not be null");
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.JUKEBOX_SONG);
        var song = registry.get(songKey);
        Objects.requireNonNull(song, "Jukebox song not found for key: " + songKey);
        return jukeboxPlayable(song);
    }

    public final T trim(ArmorTrim trim) {
        Objects.requireNonNull(trim, "Parameter 'trim' must not be null");
        item.setData(DataComponentTypes.TRIM, ItemArmorTrim.itemArmorTrim(trim).build());
        return self();
    }

    public final T potion(PotionType type) {
        Objects.requireNonNull(type, "Parameter 'type' must not be null");
        var builder = potionContentsBuilder();
        builder.potion(type);
        item.setData(DataComponentTypes.POTION_CONTENTS, builder.build());
        return self();
    }

    public final T potionEffects(PotionEffect... effects) {
        Objects.requireNonNull(effects, "Parameter 'effects' must not be null");
        var builder = potionContentsBuilder();
        Arrays.stream(effects).forEach(builder::addCustomEffect);
        item.setData(DataComponentTypes.POTION_CONTENTS, builder.build());
        return self();
    }

    private PotionContents.Builder potionContentsBuilder() {
        var existing = item.getData(DataComponentTypes.POTION_CONTENTS);
        var builder = PotionContents.potionContents();
        if (existing != null) {
            builder.potion(existing.potion())
                    .customColor(existing.customColor())
                    .customName(existing.customName());
            existing.customEffects().forEach(builder::addCustomEffect);
        }
        return builder;
    }

    public final T dye(Color color) {
        Objects.requireNonNull(color, "Parameter 'color' must not be null");
        item.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
        return self();
    }

    public final T blocksAttacks(Consumer<BlocksAttacks.Builder> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        var builder = BlocksAttacks.blocksAttacks();
        consumer.accept(builder);
        item.setData(DataComponentTypes.BLOCKS_ATTACKS, builder.build());
        return self();
    }

    public final T consumable(Consumer<Consumable.Builder> consumer) {
        Objects.requireNonNull(consumer, "Parameter 'consumer' must not be null");
        var builder = Consumable.consumable();
        consumer.accept(builder);
        item.setData(DataComponentTypes.CONSUMABLE, builder.build());
        return self();
    }

    public final T resetData(DataComponentType type) {
        Objects.requireNonNull(type, "Parameter 'type' must not be null");
        item.resetData(type);
        return self();
    }

    public final T unsetData(DataComponentType type) {
        Objects.requireNonNull(type, "Parameter 'type' must not be null");
        item.unsetData(type);
        return self();
    }

    public final boolean hasData(DataComponentType type) {
        Objects.requireNonNull(type, "Parameter 'type' must not be null");
        return item.hasData(type);
    }

    private static List<Float> boxedFloats(float[] floats) {
        var list = new ArrayList<Float>(floats.length);
        for (float value : floats) {
            list.add(value);
        }
        return list;
    }

    private static List<Boolean> boxedBooleans(boolean[] flags) {
        var list = new ArrayList<Boolean>(flags.length);
        for (boolean flag : flags) {
            list.add(flag);
        }
        return list;
    }
}

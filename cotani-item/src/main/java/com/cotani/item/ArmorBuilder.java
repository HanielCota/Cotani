package com.cotani.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ArmorBuilder extends ItemStackBuilder<ArmorBuilder> {

    private static final Set<Material> ARMOR_MATERIALS = EnumSet.noneOf(Material.class);
    private static final AtomicBoolean armorMaterialsResolved = new AtomicBoolean(false);

    private ArmorBuilder(Material material) {
        super(material);
    }

    public static ArmorBuilder of(Material material) {
        Objects.requireNonNull(material, "Parameter 'material' must not be null");

        if (!isArmorMaterial(material)) {
            throw new IllegalArgumentException("Material must be armor: " + material);
        }

        return new ArmorBuilder(material);
    }

    private static boolean isArmorMaterial(Material material) {
        if (armorMaterialsResolved.get()) {
            return ARMOR_MATERIALS.contains(material);
        }

        synchronized (ARMOR_MATERIALS) {
            if (armorMaterialsResolved.get()) {
                return ARMOR_MATERIALS.contains(material);
            }

            for (var candidate : Material.values()) {
                if (candidate.isItem() && ItemStack.of(candidate).getItemMeta() instanceof ArmorMeta) {
                    ARMOR_MATERIALS.add(candidate);
                }
            }
            armorMaterialsResolved.set(true);
            return ARMOR_MATERIALS.contains(material);
        }
    }

    @Override
    protected ArmorBuilder self() {
        return this;
    }

    public ArmorBuilder trim(ArmorTrim trim) {
        Objects.requireNonNull(trim, "Parameter 'trim' must not be null");
        item().setData(
                        DataComponentTypes.TRIM,
                        ItemArmorTrim.itemArmorTrim(trim).build());
        return self();
    }

    public ArmorBuilder trim(TrimMaterial material, TrimPattern pattern) {
        Objects.requireNonNull(material, "Parameter 'material' must not be null");
        Objects.requireNonNull(pattern, "Parameter 'pattern' must not be null");
        return trim(new ArmorTrim(material, pattern));
    }

    public ArmorBuilder removeTrim() {
        item().unsetData(DataComponentTypes.TRIM);
        return self();
    }
}

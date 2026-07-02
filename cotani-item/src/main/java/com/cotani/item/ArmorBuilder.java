package com.cotani.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemArmorTrim;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ArmorBuilder extends ItemStackBuilder<ArmorBuilder> {

    private ArmorBuilder(Material material) {
        super(material);
    }

    public static ArmorBuilder of(Material material) {
        Objects.requireNonNull(material, "Parameter 'material' must not be null");
        var preview = ItemStack.of(material);

        if (!(preview.getItemMeta() instanceof ArmorMeta)) {
            throw new IllegalArgumentException("Material must be armor: " + material);
        }

        return new ArmorBuilder(material);
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

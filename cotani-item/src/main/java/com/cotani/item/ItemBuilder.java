package com.cotani.item;

import org.bukkit.Material;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ItemBuilder extends ItemStackBuilder<ItemBuilder> {

    private ItemBuilder(Material material) {
        super(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    @Override
    protected ItemBuilder self() {
        return this;
    }
}

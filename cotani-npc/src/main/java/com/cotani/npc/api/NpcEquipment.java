package com.cotani.npc.api;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Immutable representation of equipment worn and held by a virtual NPC.
 *
 * @param helmet helmet item or null
 * @param chestplate chestplate item or null
 * @param leggings leggings item or null
 * @param boots boots item or null
 * @param mainHand main hand item or null
 * @param offHand off-hand item or null
 */
public record NpcEquipment(
        @Nullable ItemStack helmet,
        @Nullable ItemStack chestplate,
        @Nullable ItemStack leggings,
        @Nullable ItemStack boots,
        @Nullable ItemStack mainHand,
        @Nullable ItemStack offHand) {

    public static final NpcEquipment EMPTY = new NpcEquipment(null, null, null, null, null, null);

    public NpcEquipment {
        helmet = helmet != null ? helmet.clone() : null;
        chestplate = chestplate != null ? chestplate.clone() : null;
        leggings = leggings != null ? leggings.clone() : null;
        boots = boots != null ? boots.clone() : null;
        mainHand = mainHand != null ? mainHand.clone() : null;
        offHand = offHand != null ? offHand.clone() : null;
    }

    /**
     * Creates a new fluent {@link Builder}.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NpcEquipment}.
     */
    public static final class Builder {
        private @Nullable ItemStack helmet;
        private @Nullable ItemStack chestplate;
        private @Nullable ItemStack leggings;
        private @Nullable ItemStack boots;
        private @Nullable ItemStack mainHand;
        private @Nullable ItemStack offHand;

        public Builder helmet(@Nullable ItemStack helmet) {
            this.helmet = helmet != null ? helmet.clone() : null;
            return this;
        }

        public Builder chestplate(@Nullable ItemStack chestplate) {
            this.chestplate = chestplate != null ? chestplate.clone() : null;
            return this;
        }

        public Builder leggings(@Nullable ItemStack leggings) {
            this.leggings = leggings != null ? leggings.clone() : null;
            return this;
        }

        public Builder boots(@Nullable ItemStack boots) {
            this.boots = boots != null ? boots.clone() : null;
            return this;
        }

        public Builder mainHand(@Nullable ItemStack mainHand) {
            this.mainHand = mainHand != null ? mainHand.clone() : null;
            return this;
        }

        public Builder offHand(@Nullable ItemStack offHand) {
            this.offHand = offHand != null ? offHand.clone() : null;
            return this;
        }

        public NpcEquipment build() {
            return new NpcEquipment(helmet, chestplate, leggings, boots, mainHand, offHand);
        }
    }
}

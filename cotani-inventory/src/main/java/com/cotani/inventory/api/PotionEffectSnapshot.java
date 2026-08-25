package com.cotani.inventory.api;

import java.util.Objects;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.NullMarked;

/**
 * Immutable snapshot of an active potion effect on a player.
 *
 * @param type potion effect type
 * @param durationTicks remaining duration in server ticks
 * @param amplifier effect amplifier level
 * @param ambient whether the effect is ambient (from beacon)
 * @param particles whether particles are shown
 * @param icon whether the icon is visible in client UI
 */
@NullMarked
public record PotionEffectSnapshot(
        PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {

    public PotionEffectSnapshot {
        Objects.requireNonNull(type, "type");
        if (durationTicks < 0) {
            durationTicks = 0;
        }
        if (amplifier < 0) {
            amplifier = 0;
        }
    }

    /**
     * Converts a Bukkit {@link PotionEffect} into a {@link PotionEffectSnapshot}.
     *
     * @param effect bukkit potion effect
     * @return snapshot representation
     */
    public static PotionEffectSnapshot fromBukkit(PotionEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return new PotionEffectSnapshot(
                effect.getType(),
                effect.getDuration(),
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.hasParticles(),
                effect.hasIcon());
    }

    /**
     * Converts this snapshot back to a Bukkit {@link PotionEffect}.
     *
     * @return bukkit potion effect
     */
    public PotionEffect toBukkit() {
        return new PotionEffect(type, durationTicks, amplifier, ambient, particles, icon);
    }
}

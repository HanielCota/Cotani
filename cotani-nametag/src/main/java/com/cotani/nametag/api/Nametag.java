package com.cotani.nametag.api;

import com.cotani.text.MiniMessages;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jspecify.annotations.Nullable;

/**
 * Immutable representation of a player's nametag, tablist sorting priority, and team options.
 *
 * @param priority sorting priority in the tablist (lower values appear first, e.g. 1 before 10)
 * @param prefix component prefixed to the player's head nametag and tab entry
 * @param suffix component suffixed to the player's head nametag and tab entry
 * @param color name and glow outline color
 * @param visibility above-head nametag visibility rule
 * @param collisionRule entity collision rule
 * @param seeFriendlyInvisibles whether players in this team can see invisible teammates
 * @param friendlyFire whether teammates can damage each other
 */
public record Nametag(
        int priority,
        Component prefix,
        Component suffix,
        @Nullable NamedTextColor color,
        NametagVisibility visibility,
        CollisionRule collisionRule,
        boolean seeFriendlyInvisibles,
        boolean friendlyFire) {

    public static final int DEFAULT_PRIORITY = 1000;
    public static final Nametag EMPTY = builder().build();

    public Nametag {
        Objects.requireNonNull(prefix, "Parameter 'prefix' must not be null");
        Objects.requireNonNull(suffix, "Parameter 'suffix' must not be null");
        Objects.requireNonNull(visibility, "Parameter 'visibility' must not be null");
        Objects.requireNonNull(collisionRule, "Parameter 'collisionRule' must not be null");

        if (priority < 0) {
            throw new IllegalArgumentException("Priority cannot be negative: " + priority);
        }
    }

    /**
     * Creates a simple Nametag with prefix and suffix.
     *
     * @param prefix the prefix component
     * @param suffix the suffix component
     * @return a new Nametag instance
     */
    public static Nametag of(Component prefix, Component suffix) {
        return builder().prefix(prefix).suffix(suffix).build();
    }

    /**
     * Creates a simple Nametag with prefix, suffix, and priority.
     *
     * @param prefix the prefix component
     * @param suffix the suffix component
     * @param priority the tablist sorting priority
     * @return a new Nametag instance
     */
    public static Nametag of(Component prefix, Component suffix, int priority) {
        return builder().prefix(prefix).suffix(suffix).priority(priority).build();
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
     * Creates a new fluent {@link Builder} pre-populated with this nametag's values.
     *
     * @return a populated Builder instance
     */
    public Builder toBuilder() {
        return new Builder()
                .priority(priority)
                .prefix(prefix)
                .suffix(suffix)
                .color(color)
                .visibility(visibility)
                .collisionRule(collisionRule)
                .seeFriendlyInvisibles(seeFriendlyInvisibles)
                .friendlyFire(friendlyFire);
    }

    /**
     * Fluent builder for {@link Nametag}.
     */
    public static final class Builder {
        private int priority = DEFAULT_PRIORITY;
        private Component prefix = Component.empty();
        private Component suffix = Component.empty();
        private @Nullable NamedTextColor color;
        private NametagVisibility visibility = NametagVisibility.ALWAYS;
        private CollisionRule collisionRule = CollisionRule.ALWAYS;
        private boolean seeFriendlyInvisibles;
        private boolean friendlyFire = true;

        public Builder priority(int priority) {
            if (priority < 0) {
                throw new IllegalArgumentException("Priority cannot be negative: " + priority);
            }
            this.priority = priority;
            return this;
        }

        public Builder prefix(Component prefix) {
            this.prefix = Objects.requireNonNull(prefix, "Parameter 'prefix' must not be null");
            return this;
        }

        public Builder prefix(String miniMessage) {
            Objects.requireNonNull(miniMessage, "Parameter 'miniMessage' must not be null");
            return prefix(MiniMessages.parse(miniMessage));
        }

        public Builder prefixEscaped(String rawText) {
            Objects.requireNonNull(rawText, "Parameter 'rawText' must not be null");
            return prefix(MiniMessages.parse(MiniMessages.escape(rawText)));
        }

        public Builder suffix(Component suffix) {
            this.suffix = Objects.requireNonNull(suffix, "Parameter 'suffix' must not be null");
            return this;
        }

        public Builder suffix(String miniMessage) {
            Objects.requireNonNull(miniMessage, "Parameter 'miniMessage' must not be null");
            return suffix(MiniMessages.parse(miniMessage));
        }

        public Builder suffixEscaped(String rawText) {
            Objects.requireNonNull(rawText, "Parameter 'rawText' must not be null");
            return suffix(MiniMessages.parse(MiniMessages.escape(rawText)));
        }

        public Builder color(@Nullable NamedTextColor color) {
            this.color = color;
            return this;
        }

        public Builder visibility(NametagVisibility visibility) {
            this.visibility = Objects.requireNonNull(visibility, "Parameter 'visibility' must not be null");
            return this;
        }

        public Builder collisionRule(CollisionRule collisionRule) {
            this.collisionRule = Objects.requireNonNull(collisionRule, "Parameter 'collisionRule' must not be null");
            return this;
        }

        public Builder seeFriendlyInvisibles(boolean seeFriendlyInvisibles) {
            this.seeFriendlyInvisibles = seeFriendlyInvisibles;
            return this;
        }

        public Builder friendlyFire(boolean friendlyFire) {
            this.friendlyFire = friendlyFire;
            return this;
        }

        public Nametag build() {
            return new Nametag(
                    priority, prefix, suffix, color, visibility, collisionRule, seeFriendlyInvisibles, friendlyFire);
        }
    }
}

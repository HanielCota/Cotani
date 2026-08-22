package com.cotani.npc.api;

import com.cotani.text.MiniMessages;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

/**
 * Immutable configuration of a virtual player NPC.
 *
 * @param id unique identifier of the NPC
 * @param displayName display name component above head and in player list
 * @param location world spawn location
 * @param skin texture skin of the NPC
 * @param equipment equipment items worn and held
 * @param pose animation or posture pose
 * @param lookAtPlayer whether to dynamically rotate head and pitch to face nearby players
 * @param viewDistance view distance in blocks to track and render this NPC (default: 48)
 * @param glowing whether glowing outline is enabled
 * @param glowColor glow outline color
 * @param interactionHandler interaction callback invoked when clicked by a player
 */
public record Npc(
        UUID id,
        Component displayName,
        Location location,
        NpcSkin skin,
        NpcEquipment equipment,
        NpcPose pose,
        boolean lookAtPlayer,
        double viewDistance,
        boolean glowing,
        @Nullable NamedTextColor glowColor,
        Consumer<NpcInteractEvent> interactionHandler) {

    public static final double DEFAULT_VIEW_DISTANCE = 48.0;

    public Npc {
        Objects.requireNonNull(id, "Parameter 'id' must not be null");
        Objects.requireNonNull(displayName, "Parameter 'displayName' must not be null");
        Objects.requireNonNull(location, "Parameter 'location' must not be null");
        Objects.requireNonNull(skin, "Parameter 'skin' must not be null");
        Objects.requireNonNull(equipment, "Parameter 'equipment' must not be null");
        Objects.requireNonNull(pose, "Parameter 'pose' must not be null");
        Objects.requireNonNull(interactionHandler, "Parameter 'interactionHandler' must not be null");

        if (viewDistance <= 0) {
            throw new IllegalArgumentException("viewDistance must be positive: " + viewDistance);
        }
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
     * Creates a new fluent {@link Builder} pre-populated with this NPC's state.
     *
     * @return populated Builder instance
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .name(displayName)
                .location(location)
                .skin(skin)
                .equipment(equipment)
                .pose(pose)
                .lookAtPlayer(lookAtPlayer)
                .viewDistance(viewDistance)
                .glowing(glowing)
                .glowColor(glowColor)
                .onInteract(interactionHandler);
    }

    /**
     * Fluent builder for {@link Npc}.
     */
    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private Component displayName = Component.empty();
        private @Nullable Location location;
        private NpcSkin skin = NpcSkin.EMPTY;
        private NpcEquipment equipment = NpcEquipment.EMPTY;
        private NpcPose pose = NpcPose.STANDING;
        private boolean lookAtPlayer = true;
        private double viewDistance = DEFAULT_VIEW_DISTANCE;
        private boolean glowing = false;
        private @Nullable NamedTextColor glowColor;
        private Consumer<NpcInteractEvent> interactionHandler = _ -> {};

        public Builder id(UUID id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder name(Component displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder name(String miniMessageText) {
            Objects.requireNonNull(miniMessageText, "miniMessageText");
            this.displayName = MiniMessages.parse(miniMessageText);
            return this;
        }

        public Builder nameEscaped(String rawText) {
            Objects.requireNonNull(rawText, "rawText");
            this.displayName = MiniMessages.parse(MiniMessages.escape(rawText));
            return this;
        }

        public Builder location(Location location) {
            this.location = Objects.requireNonNull(location, "location").clone();
            return this;
        }

        public Builder skin(NpcSkin skin) {
            this.skin = Objects.requireNonNull(skin, "skin");
            return this;
        }

        public Builder skin(String textureValue, String signature) {
            this.skin = NpcSkin.of(textureValue, signature);
            return this;
        }

        public Builder skin(String textureValue) {
            this.skin = NpcSkin.of(textureValue);
            return this;
        }

        public Builder equipment(NpcEquipment equipment) {
            this.equipment = Objects.requireNonNull(equipment, "equipment");
            return this;
        }

        public Builder pose(NpcPose pose) {
            this.pose = Objects.requireNonNull(pose, "pose");
            return this;
        }

        public Builder lookAtPlayer(boolean lookAtPlayer) {
            this.lookAtPlayer = lookAtPlayer;
            return this;
        }

        public Builder viewDistance(double viewDistance) {
            if (viewDistance <= 0) {
                throw new IllegalArgumentException("viewDistance must be positive: " + viewDistance);
            }
            this.viewDistance = viewDistance;
            return this;
        }

        public Builder glowing(boolean glowing) {
            this.glowing = glowing;
            return this;
        }

        public Builder glowColor(@Nullable NamedTextColor glowColor) {
            this.glowColor = glowColor;
            return this;
        }

        public Builder onInteract(Consumer<NpcInteractEvent> interactionHandler) {
            this.interactionHandler = Objects.requireNonNull(interactionHandler, "interactionHandler");
            return this;
        }

        public Npc build() {
            if (location == null) {
                throw new IllegalStateException("Npc 'location' must be specified before building");
            }
            return new Npc(
                    id,
                    displayName,
                    location.clone(),
                    skin,
                    equipment,
                    pose,
                    lookAtPlayer,
                    viewDistance,
                    glowing,
                    glowColor,
                    interactionHandler);
        }
    }
}

package com.cotani.npc.api;

import com.cotani.AsyncCloseable;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;

/**
 * Service contract for managing virtual player NPCs, packet rendering, look-at targeting, and click interactions.
 */
public interface NpcModule extends AutoCloseable, AsyncCloseable {

    /**
     * Creates and registers a new NPC with a location and name.
     *
     * @param location spawn location
     * @param name MiniMessage formatted name
     * @return created Npc instance
     */
    Npc create(Location location, String name);

    /**
     * Creates and registers a new NPC using a fluent configuration lambda.
     *
     * @param builderConsumer configuration consumer
     * @return created Npc instance
     */
    Npc create(Consumer<Npc.Builder> builderConsumer);

    /**
     * Spawns an NPC, making it visible to nearby players within its view distance.
     *
     * @param npc the NPC to spawn
     */
    void spawn(Npc npc);

    /**
     * Despawns an NPC, removing it from all viewers.
     *
     * @param npc the NPC to despawn
     */
    void despawn(Npc npc);

    /**
     * Despawns an NPC by its unique identifier.
     *
     * @param npcId the NPC UUID to despawn
     */
    void despawn(UUID npcId);

    /**
     * Finds an NPC by its unique identifier, if registered.
     *
     * @param npcId the NPC UUID
     * @return Optional containing the NPC, or empty
     */
    Optional<Npc> findNpc(UUID npcId);

    /**
     * Returns an unmodifiable snapshot of all registered NPCs.
     *
     * @return collection of all active NPCs
     */
    Collection<Npc> allNpcs();

    /**
     * Updates an NPC's position and synchronizes movement packets to viewers.
     *
     * @param npcId the NPC UUID
     * @param newLocation new location
     */
    void updateLocation(UUID npcId, Location newLocation);

    /**
     * Updates an NPC's skin and respawns it for viewers.
     *
     * @param npcId the NPC UUID
     * @param skin new skin
     */
    void updateSkin(UUID npcId, NpcSkin skin);

    /**
     * Updates an NPC's equipment and synchronizes equipment packets to viewers.
     *
     * @param npcId the NPC UUID
     * @param equipment new equipment
     */
    void updateEquipment(UUID npcId, NpcEquipment equipment);

    /**
     * Refreshes an NPC for all online viewers.
     *
     * @param npc the NPC to refresh
     */
    void refresh(Npc npc);

    /**
     * Refreshes all active NPCs for all online viewers.
     */
    void refreshAll();

    @Override
    void close();
}

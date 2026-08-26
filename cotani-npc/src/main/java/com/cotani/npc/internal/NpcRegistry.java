package com.cotani.npc.internal;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory registry for virtual NPCs.
 */
@InternalApi
public final class NpcRegistry {

    private static final String NPC_NULL_MSG = "Parameter 'npc' must not be null";
    private static final String NPC_ID_NULL_MSG = "Parameter 'npcId' must not be null";

    private final Map<UUID, Npc> npcs = new ConcurrentHashMap<>();

    public void register(Npc npc) {
        Objects.requireNonNull(npc, NPC_NULL_MSG);
        npcs.put(npc.id(), npc);
    }

    public Optional<Npc> unregister(UUID npcId) {
        Objects.requireNonNull(npcId, NPC_ID_NULL_MSG);
        return Optional.ofNullable(npcs.remove(npcId));
    }

    public Optional<Npc> find(UUID npcId) {
        Objects.requireNonNull(npcId, NPC_ID_NULL_MSG);
        return Optional.ofNullable(npcs.get(npcId));
    }

    /**
     * Returns an immutable snapshot of all registered NPCs.
     *
     * <p>The returned collection is an independent copy; later registrations and removals are not
     * reflected in it.
     */
    public Collection<Npc> all() {
        return List.copyOf(npcs.values());
    }

    public boolean contains(UUID npcId) {
        Objects.requireNonNull(npcId, NPC_ID_NULL_MSG);
        return npcs.containsKey(npcId);
    }

    public void update(Npc npc) {
        Objects.requireNonNull(npc, NPC_NULL_MSG);
        npcs.put(npc.id(), npc);
    }

    public void clear() {
        npcs.clear();
    }
}

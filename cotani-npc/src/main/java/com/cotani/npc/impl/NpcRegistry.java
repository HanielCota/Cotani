package com.cotani.npc.impl;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import java.util.Collection;
import java.util.Collections;
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

    private final Map<UUID, Npc> npcs = new ConcurrentHashMap<>();

    public void register(Npc npc) {
        Objects.requireNonNull(npc, "Parameter 'npc' must not be null");
        npcs.put(npc.id(), npc);
    }

    public Optional<Npc> unregister(UUID npcId) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        return Optional.ofNullable(npcs.remove(npcId));
    }

    public Optional<Npc> find(UUID npcId) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        return Optional.ofNullable(npcs.get(npcId));
    }

    public Collection<Npc> all() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    public boolean contains(UUID npcId) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        return npcs.containsKey(npcId);
    }

    public void update(Npc npc) {
        Objects.requireNonNull(npc, "Parameter 'npc' must not be null");
        npcs.put(npc.id(), npc);
    }

    public void clear() {
        npcs.clear();
    }
}

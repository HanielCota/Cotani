package com.cotani.npc.api;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

/**
 * Pluggable adapter responsible for dispatching virtual NPC network packets (e.g. via PacketEvents or custom packet drivers).
 */
@NullMarked
public interface NpcPacketAdapter {
    void sendSpawn(Player viewer, Npc npc);

    void sendDespawn(Player viewer, Npc npc);

    void sendRotation(Player viewer, Npc npc, float yaw, float pitch);

    void sendEquipment(Player viewer, Npc npc);

    static NpcPacketAdapter noop() {
        return new NpcPacketAdapter() {
            @Override
            public void sendSpawn(Player viewer, Npc npc) {}

            @Override
            public void sendDespawn(Player viewer, Npc npc) {}

            @Override
            public void sendRotation(Player viewer, Npc npc, float yaw, float pitch) {}

            @Override
            public void sendEquipment(Player viewer, Npc npc) {}
        };
    }
}

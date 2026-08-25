package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cotani.npc.api.Npc;
import com.cotani.npc.internal.NpcRegistry;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NpcRegistryTest {

    private NpcRegistry registry;
    private Npc npc;
    private UUID id;

    @BeforeEach
    void setUp() {
        registry = new NpcRegistry();
        id = UUID.randomUUID();
        var world = mock(World.class);
        npc = Npc.builder()
                .id(id)
                .name("NPC1")
                .location(new Location(world, 10, 60, 10))
                .build();
    }

    @Test
    void shouldRegisterAndRetrieveNpc() {
        registry.register(npc);

        assertTrue(registry.contains(id));
        assertEquals(npc, registry.find(id).orElse(null));
        assertEquals(1, registry.all().size());

        var removed = registry.unregister(id);
        assertTrue(removed.isPresent());
        assertEquals(npc, removed.get());
        assertFalse(registry.contains(id));
    }

    @Test
    void shouldUpdateNpc() {
        registry.register(npc);

        var updated = npc.toBuilder().name("NPC_Updated").build();
        registry.update(updated);

        assertEquals(
                "NPC_Updated",
                registry.find(id)
                        .map(n -> ((net.kyori.adventure.text.TextComponent) n.displayName()).content())
                        .orElse(""));
    }

    @Test
    void shouldClearAllNpcs() {
        registry.register(npc);
        registry.clear();

        assertTrue(registry.all().isEmpty());
    }
}

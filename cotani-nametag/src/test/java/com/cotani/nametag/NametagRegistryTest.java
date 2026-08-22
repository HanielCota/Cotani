package com.cotani.nametag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.nametag.api.Nametag;
import com.cotani.nametag.impl.NametagRegistry;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NametagRegistryTest {

    private NametagRegistry registry;
    private Player viewer;
    private Player target;
    private UUID viewerId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        registry = new NametagRegistry();
        viewer = mock(Player.class);
        target = mock(Player.class);

        viewerId = UUID.randomUUID();
        targetId = UUID.randomUUID();

        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(target.getUniqueId()).thenReturn(targetId);
    }

    @Test
    void shouldStoreAndRetrieveGlobalTags() {
        var tag = Nametag.of(Component.text("[VIP] "), Component.empty(), 5);
        registry.setGlobal(targetId, tag);

        assertEquals(tag, registry.getGlobal(targetId).orElse(null));
        assertEquals(tag, registry.resolveEffective(viewer, target));

        registry.removeGlobal(targetId);
        assertFalse(registry.getGlobal(targetId).isPresent());
        assertEquals(Nametag.EMPTY, registry.resolveEffective(viewer, target));
    }

    @Test
    void shouldStoreViewerOverrideAndPrecedeGlobalTag() {
        var globalTag = Nametag.of(Component.text("[Global] "), Component.empty(), 10);
        var overrideTag = Nametag.of(Component.text("[Friend] "), Component.empty(), 1);

        registry.setGlobal(targetId, globalTag);
        registry.setOverride(viewerId, targetId, overrideTag);

        assertEquals(overrideTag, registry.resolveEffective(viewer, target));

        var otherViewer = mock(Player.class);
        when(otherViewer.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals(globalTag, registry.resolveEffective(otherViewer, target));

        registry.removeOverride(viewerId, targetId);
        assertEquals(globalTag, registry.resolveEffective(viewer, target));
    }

    @Test
    void shouldPrioritizeDynamicProviderOverOverrides() {
        var globalTag = Nametag.of(Component.text("[Global] "), Component.empty(), 10);
        var overrideTag = Nametag.of(Component.text("[Friend] "), Component.empty(), 5);
        var providerTag = Nametag.of(Component.text("[GuildMaster] "), Component.empty(), 1);

        registry.setGlobal(targetId, globalTag);
        registry.setOverride(viewerId, targetId, overrideTag);
        registry.registerProvider((v, t) -> Optional.of(providerTag));

        assertEquals(providerTag, registry.resolveEffective(viewer, target));

        registry.unregisterProvider((v, t) -> Optional.of(providerTag));
        // Reset registry and re-verify after clear
        registry.clear();
        assertEquals(Nametag.EMPTY, registry.resolveEffective(viewer, target));
    }

    @Test
    void shouldRemoveAllDataForPlayer() {
        var globalTag = Nametag.of(Component.text("[Global] "), Component.empty(), 10);
        var overrideTag = Nametag.of(Component.text("[Friend] "), Component.empty(), 5);

        registry.setGlobal(targetId, globalTag);
        registry.setOverride(viewerId, targetId, overrideTag);

        registry.removeAll(targetId);

        assertFalse(registry.getGlobal(targetId).isPresent());
        assertEquals(Nametag.EMPTY, registry.resolveEffective(viewer, target));
    }

    @Test
    void shouldPruneEmptyTagsAutomatically() {
        registry.setGlobal(targetId, Nametag.EMPTY);
        assertFalse(registry.getGlobal(targetId).isPresent());

        registry.setOverride(viewerId, targetId, Nametag.EMPTY);
        assertEquals(Nametag.EMPTY, registry.resolveEffective(viewer, target));
    }
}

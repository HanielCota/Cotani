package com.cotani.nametag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.cotani.nametag.api.Nametag;
import com.cotani.nametag.api.NametagModule;
import com.cotani.nametag.internal.DefaultNametagModule;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DefaultNametagModuleTest {

    private MockedStatic<Bukkit> bukkitMock;
    private Plugin plugin;
    private Server server;
    private PaperTaskScheduler scheduler;
    private Player viewer;
    private Player target;
    private Scoreboard scoreboard;
    private Team team;
    private NametagModule module;

    private UUID viewerId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        bukkitMock = mockStatic(Bukkit.class);
        server = mock(Server.class);
        bukkitMock.when(Bukkit::getServer).thenReturn(server);

        plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);

        var pluginManager = mock(org.bukkit.plugin.PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);

        scheduler = mock(PaperTaskScheduler.class);
        viewer = mock(Player.class);
        target = mock(Player.class);
        scoreboard = mock(Scoreboard.class);
        team = mock(Team.class);

        viewerId = UUID.randomUUID();
        targetId = UUID.randomUUID();

        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.getName()).thenReturn("ViewerPlayer");
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getScoreboard()).thenReturn(scoreboard);

        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("TargetPlayer");
        when(target.isOnline()).thenReturn(true);
        when(target.getScoreboard()).thenReturn(scoreboard);

        when(server.getPlayer(viewerId)).thenReturn(viewer);
        when(server.getPlayer(targetId)).thenReturn(target);
        bukkitMock.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(viewer);
        bukkitMock.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);

        when(server.getOnlinePlayers()).thenAnswer(_ -> List.of(viewer, target));
        bukkitMock.when(Bukkit::getOnlinePlayers).thenAnswer(_ -> List.of(viewer, target));

        when(scoreboard.getTeam(any())).thenReturn(team);
        when(scoreboard.registerNewTeam(any())).thenReturn(team);

        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .entity(any(UUID.class), any(Runnable.class));

        module = new DefaultNametagModule(plugin, scheduler);
    }

    @AfterEach
    void tearDown() {
        if (bukkitMock != null) {
            bukkitMock.close();
        }
    }

    @Test
    void shouldApplyAndRetrieveGlobalNametag() {
        var tag = Nametag.of(Component.text("[VIP] "), Component.empty(), 50);
        module.apply(targetId, tag);

        var retrieved = module.getNametag(targetId);
        assertTrue(retrieved.isPresent());
        assertEquals(tag, retrieved.get());

        assertEquals(tag, module.getEffectiveNametag(viewer, target));
    }

    @Test
    void shouldApplyViewerOverrideAndPrecedeGlobalTag() {
        var globalTag = Nametag.of(Component.text("[Global] "), Component.empty());
        var overrideTag = Nametag.of(Component.text("[Friend] "), Component.empty(), 1);

        module.apply(targetId, globalTag);
        module.applyForViewer(viewerId, targetId, overrideTag);

        assertEquals(overrideTag, module.getEffectiveNametag(viewer, target));

        module.resetForViewer(viewerId, targetId);
        assertEquals(globalTag, module.getEffectiveNametag(viewer, target));
    }

    @Test
    void shouldPrioritizeProviderOverOverrides() {
        var globalTag = Nametag.of(Component.text("[Global] "), Component.empty());
        var overrideTag = Nametag.of(Component.text("[Override] "), Component.empty());
        var providerTag = Nametag.of(Component.text("[ClanLeader] "), Component.empty(), 1);

        module.apply(targetId, globalTag);
        module.applyForViewer(viewerId, targetId, overrideTag);

        module.registerProvider((v, t) -> Optional.of(providerTag));

        assertEquals(providerTag, module.getEffectiveNametag(viewer, target));

        module.unregisterProvider((v, t) -> Optional.of(providerTag));
    }

    @Test
    void shouldResetAllTagsForPlayer() {
        var globalTag = Nametag.of(Component.text("[VIP] "), Component.empty());
        var overrideTag = Nametag.of(Component.text("[Target] "), Component.empty());

        module.apply(targetId, globalTag);
        module.applyForViewer(viewerId, targetId, overrideTag);

        module.resetAll(targetId);

        assertFalse(module.getNametag(targetId).isPresent());
        assertEquals(Nametag.EMPTY, module.getEffectiveNametag(viewer, target));
    }

    @Test
    void shouldHandleFailingProviderGracefully() {
        var globalTag = Nametag.of(Component.text("[Global] "), Component.empty());
        module.apply(targetId, globalTag);

        module.registerProvider((v, t) -> {
            throw new RuntimeException("Test failure");
        });

        assertEquals(globalTag, module.getEffectiveNametag(viewer, target));
    }

    @Test
    void shouldRefreshCleanly() {
        module.refresh(viewer);
        module.refreshAll();
    }

    @Test
    void shouldApplyAndResetBatch() {
        var id1 = UUID.randomUUID();
        var id2 = UUID.randomUUID();
        var tag1 = Nametag.of(Component.text("[VIP] "), Component.empty(), 10);
        var tag2 = Nametag.of(Component.text("[MVP] "), Component.empty(), 20);

        module.applyBatch(Map.of(id1, tag1, id2, tag2));

        assertEquals(tag1, module.getNametag(id1).orElse(null));
        assertEquals(tag2, module.getNametag(id2).orElse(null));

        module.resetBatch(List.of(id1, id2));

        assertFalse(module.getNametag(id1).isPresent());
        assertFalse(module.getNametag(id2).isPresent());
    }

    @Test
    void shouldCloseAsyncCleanly() {
        var future = module.closeAsync().toCompletableFuture();
        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
    }
}

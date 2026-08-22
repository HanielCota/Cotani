package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.npc.api.Npc;
import com.cotani.npc.api.NpcInteractEvent;
import com.cotani.npc.impl.DefaultNpcModule;
import com.cotani.npc.impl.NpcPlayerListener;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NpcPlayerListenerTest {

    private Plugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private PaperTaskScheduler scheduler;
    private DefaultNpcModule module;
    private NpcPlayerListener listener;
    private World world;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        scheduler = mock(PaperTaskScheduler.class);
        var scheduledTask = mock(SchedulerTask.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(scheduler.asyncTimer(
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.any(Duration.class),
                        org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(scheduledTask);

        module = new DefaultNpcModule(plugin, scheduler);
        listener = new NpcPlayerListener(module);
        world = mock(World.class);
    }

    @Test
    void shouldHandlePlayerQuitEvent() {
        var player = mock(Player.class);
        var playerId = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        var quitEvent = mock(PlayerQuitEvent.class);
        when(quitEvent.getPlayer()).thenReturn(player);

        listener.onPlayerQuit(quitEvent);

        assertNotNull(listener);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldTriggerNpcInteractionOnAiming() {
        var player = mock(Player.class);
        var eyeLoc = new Location(world, 0, 64 + 1.62, 0);
        when(player.getEyeLocation()).thenReturn(eyeLoc);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        var receivedEvent = new AtomicReference<NpcInteractEvent>();
        var npcLoc = new Location(world, 0, 64, 3); // 3 blocks North/South

        var npc = Npc.builder()
                .location(npcLoc)
                .name("ClickableNpc")
                .onInteract(receivedEvent::set)
                .build();

        module.spawn(npc);

        // Aiming directly at NPC (+Z)
        eyeLoc.setDirection(new Vector(0, -0.2, 1).normalize());

        var interactEvent =
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, null, EquipmentSlot.HAND);

        listener.onPlayerInteract(interactEvent);

        assertNotNull(receivedEvent.get());
        assertEquals(npc, receivedEvent.get().npc());
        assertEquals(NpcInteractEvent.Action.RIGHT_CLICK, receivedEvent.get().action());
    }
}

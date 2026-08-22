package com.cotani.region;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.region.api.Region3D;
import com.cotani.region.api.RegionFlag;
import com.cotani.region.impl.DefaultRegionModule;
import com.cotani.region.impl.RegionProtectionListener;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.ArrayList;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegionProtectionListenerTest {

    private Plugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private PaperTaskScheduler scheduler;
    private DefaultRegionModule module;
    private RegionProtectionListener listener;
    private World world;
    private UUID worldId;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        scheduler = mock(PaperTaskScheduler.class);
        world = mock(World.class);
        worldId = UUID.randomUUID();

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(world.getUID()).thenReturn(worldId);

        module = new DefaultRegionModule(plugin, scheduler);
        listener = new RegionProtectionListener(module);
    }

    @Test
    void shouldCancelBlockBreakInProtectedRegion() {
        var region = Region3D.builder("no-break", worldId)
                .bounds(0, 0, 0, 50, 50, 50)
                .flag(RegionFlag.BLOCK_BREAK, false)
                .build();

        module.registerRegion(region);

        var player = mock(Player.class);
        var block = mock(Block.class);
        when(block.getLocation()).thenReturn(new Location(world, 10, 10, 10));

        var breakEvent = new BlockBreakEvent(block, player);
        listener.onBlockBreak(breakEvent);

        assertTrue(breakEvent.isCancelled());
    }

    @Test
    void shouldCancelMobSpawnInProtectedRegion() {
        var region = Region3D.builder("safe-zone", worldId)
                .bounds(0, 0, 0, 50, 50, 50)
                .flag(RegionFlag.MOB_SPAWN, false)
                .build();

        module.registerRegion(region);

        var spawnLoc = new Location(world, 20, 20, 20);
        var spawnEvent = mock(CreatureSpawnEvent.class);
        when(spawnEvent.getLocation()).thenReturn(spawnLoc);
        when(spawnEvent.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);

        listener.onCreatureSpawn(spawnEvent);

        verify(spawnEvent).setCancelled(true);
    }

    @Test
    void shouldCancelExplosionsInProtectedRegion() {
        var region = Region3D.builder("anti-explosion", worldId)
                .bounds(0, 0, 0, 50, 50, 50)
                .flag(RegionFlag.EXPLOSIONS, false)
                .build();

        module.registerRegion(region);

        var loc = new Location(world, 25, 25, 25);
        var blocks = new ArrayList<Block>();
        blocks.add(mock(Block.class));

        var explodeEvent = mock(EntityExplodeEvent.class);
        when(explodeEvent.getLocation()).thenReturn(loc);
        when(explodeEvent.blockList()).thenReturn(blocks);

        listener.onEntityExplode(explodeEvent);

        verify(explodeEvent).setCancelled(true);
        assertTrue(blocks.isEmpty());
    }

    @Test
    void shouldDenyEntryIntoRestrictedRegion() {
        var region = Region3D.builder("restricted", worldId)
                .bounds(10, 10, 10, 50, 50, 50)
                .flag(RegionFlag.ENTRY, false)
                .build();

        module.registerRegion(region);

        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        var from = new Location(world, 0, 10, 0); // Outside
        var to = new Location(world, 20, 20, 20); // Inside restricted region

        var moveEvent = new PlayerMoveEvent(player, from, to);
        listener.onPlayerMove(moveEvent);

        assertTrue(moveEvent.isCancelled());
    }
}

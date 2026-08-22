package com.cotani.region;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.region.api.Region3D;
import com.cotani.region.api.RegionFlag;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class CotaniRegionsTest {

    @Test
    void shouldCreateModuleAndEvaluateFlags() {
        var plugin = mock(Plugin.class);
        var server = mock(Server.class);
        var pluginManager = mock(PluginManager.class);
        var scheduler = mock(PaperTaskScheduler.class);
        var world = mock(World.class);
        var worldId = UUID.randomUUID();

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(world.getUID()).thenReturn(worldId);

        var module = CotaniRegions.create(plugin, scheduler);
        assertNotNull(module);

        var safeRegion = Region3D.builder("safe", worldId)
                .bounds(0, 0, 0, 50, 50, 50)
                .flag(RegionFlag.PVP, false)
                .build();

        module.registerRegion(safeRegion);

        var locInside = new Location(world, 10, 10, 10);
        assertFalse(module.isFlagAllowed(locInside, RegionFlag.PVP, true));

        var locOutside = new Location(world, 100, 10, 100);
        assertTrue(module.isFlagAllowed(locOutside, RegionFlag.PVP, true));

        module.close();
    }
}

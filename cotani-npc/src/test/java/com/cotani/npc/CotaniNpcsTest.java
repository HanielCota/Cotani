package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class CotaniNpcsTest {

    @Test
    void shouldCreateNpcModule() {
        var plugin = mock(Plugin.class);
        var server = mock(Server.class);
        var pluginManager = mock(PluginManager.class);
        var scheduler = mock(PaperTaskScheduler.class);
        var scheduledTask = mock(SchedulerTask.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(scheduler.asyncTimer(
                        org.mockito.ArgumentMatchers.any(Runnable.class),
                        org.mockito.ArgumentMatchers.any(Duration.class),
                        org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(scheduledTask);

        var module = CotaniNpcs.create(plugin, scheduler);
        assertNotNull(module);
    }
}

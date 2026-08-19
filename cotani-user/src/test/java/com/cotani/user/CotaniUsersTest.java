package com.cotani.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.user.api.UserModule;
import com.cotani.user.api.UserModuleOptions;
import com.cotani.user.internal.listener.UserListener;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"NullAway", "removal"})
class CotaniUsersTest {
    private final Plugin plugin = mock(Plugin.class);
    private final Server server = mock(Server.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final CotaniStorage storage = mock(CotaniStorage.class);
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(CotaniUsersTest.class.getName()));
        when(scheduler.asyncTimer(any(Runnable.class), any(Duration.class), any(Duration.class)))
                .thenReturn(SchedulerTask.noop());
    }

    @Test
    void createWithOptionsWiresModule() {
        UserModule module = CotaniUsers.create(plugin, storage, scheduler, UserModuleOptions.defaults());

        assertNotNull(module);
        assertNotNull(module.userService());
        verify(pluginManager).registerEvents(any(UserListener.class), eq(plugin));
    }

    @Test
    void createWithoutOptionsUsesDefaultOptions() {
        UserModule module = CotaniUsers.create(plugin, storage, scheduler);

        assertNotNull(module);
        verify(scheduler).asyncTimer(any(Runnable.class), eq(Duration.ofMinutes(5)), eq(Duration.ofMinutes(5)));
    }

    @Test
    void createRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> CotaniUsers.create(null, storage, scheduler));
        assertThrows(NullPointerException.class, () -> CotaniUsers.create(plugin, null, scheduler));
        assertThrows(NullPointerException.class, () -> CotaniUsers.create(plugin, storage, null));
        assertThrows(NullPointerException.class, () -> CotaniUsers.create(plugin, storage, scheduler, null));
    }

    @Test
    void migrationsDeclaresCreateUsersTableMigration() {
        List<Migration> migrations = CotaniUsers.migrations();

        assertEquals(1, migrations.size());
        assertEquals(1, migrations.getFirst().version());
        assertEquals("Create Cotani users table", migrations.getFirst().description());
    }
}

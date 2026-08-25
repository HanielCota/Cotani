package com.cotani.user.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.CotaniCloseException;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.user.api.UserModuleOptions;
import com.cotani.user.internal.listener.UserListener;
import com.cotani.user.internal.service.SimpleUserService;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({"NullAway", "removal"})
class DefaultUserModuleTest {
    private final Plugin plugin = mock(Plugin.class);
    private final Server server = mock(Server.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
    private final SimpleUserService service = mock(SimpleUserService.class);

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(DefaultUserModuleTest.class.getName()));
        when(scheduler.asyncTimer(any(Runnable.class), any(Duration.class), any(Duration.class)))
                .thenReturn(SchedulerTask.noop());
    }

    @Test
    void createWithServiceRegistersListenerAndReturnsModule() {
        DefaultUserModule module =
                DefaultUserModule.createWithService(plugin, scheduler, UserModuleOptions.defaults(), service);

        assertNotNull(module);
        assertSame(service, module.internalUserService());
        verify(pluginManager).registerEvents(any(UserListener.class), eq(plugin));
    }

    @Test
    void createWithServiceClosesLifecycleWhenRegistrationFails() {
        when(service.saveAll()).thenReturn(CompletableFuture.completedFuture(null));
        RuntimeException failure = new IllegalStateException("registration failed");
        doThrow(failure).when(pluginManager).registerEvents(any(), any());

        var exception = assertThrows(
                IllegalStateException.class,
                () -> DefaultUserModule.createWithService(plugin, scheduler, UserModuleOptions.defaults(), service));

        assertEquals("Could not initialize user module", exception.getMessage());
        assertSame(failure, exception.getCause());
        verify(service).saveAll();
    }

    @Test
    void closeTriggersSaveAllAndClearsCache() {
        when(service.saveAll()).thenReturn(CompletableFuture.completedFuture(null));

        DefaultUserModule module =
                DefaultUserModule.createWithService(plugin, scheduler, UserModuleOptions.defaults(), service);

        module.close();

        verify(service).saveAll();
        verify(service).clearCache();
    }

    @Test
    void closeLogsAndDoesNotClearCacheWhenSaveFails() {
        RuntimeException saveFailure = new RuntimeException("save failed");
        when(service.saveAll()).thenReturn(CompletableFuture.failedFuture(saveFailure));

        DefaultUserModule module =
                DefaultUserModule.createWithService(plugin, scheduler, UserModuleOptions.defaults(), service);

        var exception = assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> module.closeAsync().toCompletableFuture().join());
        var closeFailure = assertInstanceOf(CotaniCloseException.class, exception.getCause());
        assertSame(saveFailure, closeFailure.getCause());

        verify(service).saveAll();
        verify(service, never()).clearCache();
    }

    @Test
    void closeWaitsForAsyncSaveToComplete() {
        CompletableFuture<Void> saveFuture = new CompletableFuture<>();
        when(service.saveAll()).thenReturn(saveFuture);

        DefaultUserModule module =
                DefaultUserModule.createWithService(plugin, scheduler, UserModuleOptions.defaults(), service);

        var closeFuture = module.closeAsync().toCompletableFuture();
        assertFalse(closeFuture.isDone());

        saveFuture.complete(null);

        assertDoesNotThrow(() -> closeFuture.join());
        verify(service).clearCache();
    }

    @Test
    void autoSaveTaskIsScheduledWhenEnabled() {
        UserModuleOptions options = new UserModuleOptions(
                true, Duration.ofSeconds(5), UserModuleOptions.defaults().loadFailureMessage());

        DefaultUserModule.createWithService(plugin, scheduler, options, service);

        verify(scheduler).asyncTimer(any(Runnable.class), eq(Duration.ofSeconds(5)), eq(Duration.ofSeconds(5)));
    }

    @Test
    void autoSaveTaskRunsSaveAll() {
        UserModuleOptions options = new UserModuleOptions(
                true, Duration.ofSeconds(5), UserModuleOptions.defaults().loadFailureMessage());
        when(service.saveAll()).thenReturn(CompletableFuture.completedFuture(null));

        DefaultUserModule.createWithService(plugin, scheduler, options, service);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).asyncTimer(captor.capture(), any(Duration.class), any(Duration.class));

        captor.getValue().run();

        verify(service).saveAll();
    }
}

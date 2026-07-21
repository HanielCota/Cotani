package com.cotani.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

@SuppressWarnings({"NullAway", "unchecked", "removal"})
class CotaniConfigsBuilderTest {

    @Test
    void loadAsyncReturnsStageThatCompletesAfterReload(@TempDir Path tempDir) {
        Plugin plugin = mockPlugin(tempDir);
        PaperTaskScheduler scheduler = mockScheduler();
        TaskChain<Void> chain = mockChain();
        CompletableFuture<Void> pendingReload = new CompletableFuture<>();
        when(chain.toCompletionStage()).thenReturn(pendingReload);
        when(scheduler.supplyAsync(any(Supplier.class))).thenReturn(chain);

        CotaniConfigsBuilder builder =
                new CotaniConfigsBuilder(plugin).scheduler(scheduler).file("config.yml");

        var stage = builder.loadAsync();
        var future = stage.toCompletableFuture();

        assertFalse(future.isDone());

        pendingReload.complete(null);

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
        assertNotNull(future.join());
    }

    @Test
    void loadAsyncReturnsFailedStageWhenReloadFails(@TempDir Path tempDir) {
        Plugin plugin = mockPlugin(tempDir);
        PaperTaskScheduler scheduler = mockScheduler();
        TaskChain<Void> chain = mockChain();
        RuntimeException reloadFailure = new RuntimeException("reload failed");
        when(chain.toCompletionStage()).thenReturn(CompletableFuture.failedFuture(reloadFailure));
        when(scheduler.supplyAsync(any(Supplier.class))).thenReturn(chain);

        CotaniConfigsBuilder builder =
                new CotaniConfigsBuilder(plugin).scheduler(scheduler).file("config.yml");

        var future = builder.loadAsync().toCompletableFuture();

        assertTrue(future.isCompletedExceptionally());
        var exception = assertThrows(java.util.concurrent.CompletionException.class, future::join);
        assertSame(reloadFailure, exception.getCause());
    }

    @Test
    void loadAsyncSchedulesReloadOnAsyncExecutor(@TempDir Path tempDir) {
        Plugin plugin = mockPlugin(tempDir);
        PaperTaskScheduler scheduler = mockScheduler();
        TaskChain<Void> chain = mockChain();
        when(chain.toCompletionStage()).thenReturn(CompletableFuture.completedFuture(null));
        when(scheduler.supplyAsync(any(Supplier.class))).thenReturn(chain);

        CotaniConfigsBuilder builder =
                new CotaniConfigsBuilder(plugin).scheduler(scheduler).file("config.yml");

        builder.loadAsync().toCompletableFuture().join();

        verify(scheduler).supplyAsync(any(Supplier.class));
    }

    @Test
    void loadWithoutSchedulerThrows(@TempDir Path tempDir) {
        Plugin plugin = mockPlugin(tempDir);
        CotaniConfigsBuilder builder = new CotaniConfigsBuilder(plugin).file("config.yml");

        assertThrows(IllegalStateException.class, builder::load);
    }

    private static Plugin mockPlugin(Path dataFolder) {
        Plugin plugin = Mockito.mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        return plugin;
    }

    private static PaperTaskScheduler mockScheduler() {
        return Mockito.mock(PaperTaskScheduler.class);
    }

    @SuppressWarnings("unchecked")
    private static TaskChain<Void> mockChain() {
        return Mockito.mock(TaskChain.class);
    }
}

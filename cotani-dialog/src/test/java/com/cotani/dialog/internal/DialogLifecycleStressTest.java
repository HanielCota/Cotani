package com.cotani.dialog.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.dialog.api.CancelReason;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class DialogLifecycleStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void oneThousandPlayerPromptsRegisterOverrideCancelAndLeaveNoActiveState() {
        var plugin = mock(Plugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        var service = new DefaultDialogService(plugin, mock(PaperTaskScheduler.class));
        var prompts = StressTestSupport.concurrent(
                "dialog", "register-prompt", StressTestSupport.MINIMUM_ITERATIONS, 32, TIMEOUT, index -> {
                    var prompt = new RecordingPrompt(new UUID(0x6469616cL, index + 1L));
                    service.registerActivePrompt(prompt);
                    return CompletableFuture.completedFuture(prompt);
                });
        assertEquals(StressTestSupport.MINIMUM_ITERATIONS, service.activePromptsCount());

        StressTestSupport.concurrent(
                "dialog",
                "cancel-on-quit",
                StressTestSupport.MINIMUM_ITERATIONS,
                32,
                TIMEOUT,
                index -> CompletableFuture.completedFuture(
                        service.cancelPrompt(prompts.get(index).playerId(), CancelReason.PLAYER_QUIT)));
        assertEquals(0, service.activePromptsCount());
        assertTrue(prompts.stream().allMatch(prompt -> prompt.reason.get() == CancelReason.PLAYER_QUIT));
        service.close();
    }

    private static final class RecordingPrompt implements ActivePrompt {
        private final UUID playerId;
        private final AtomicReference<CancelReason> reason = new AtomicReference<>();

        private RecordingPrompt(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public UUID playerId() {
            return playerId;
        }

        @Override
        public void cancel(CancelReason reason) {
            this.reason.set(reason);
        }
    }
}

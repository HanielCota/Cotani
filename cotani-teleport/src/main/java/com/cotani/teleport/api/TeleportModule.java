package com.cotani.teleport.api;

import com.cotani.AsyncCloseable;
import com.cotani.Cotani;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.config.TeleportOptionsFactory;
import com.cotani.teleport.policy.TeleportCooldownService;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TeleportModule extends AutoCloseable, AsyncCloseable {
    Cotani cotani();

    TeleportService teleportService();

    PendingTeleportService pendingTeleportService();

    TeleportCooldownService cooldownService();

    TeleportOptionsFactory options();

    PaperTaskScheduler scheduler();

    @Override
    void close();

    @Override
    CompletionStage<Void> closeAsync();
}

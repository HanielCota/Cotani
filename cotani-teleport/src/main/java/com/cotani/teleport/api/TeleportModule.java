package com.cotani.teleport.api;

import com.cotani.Cotani;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.config.TeleportOptionsFactory;
import com.cotani.teleport.policy.TeleportCooldownService;

public interface TeleportModule extends AutoCloseable {

    Cotani cotani();

    TeleportService teleportService();

    PendingTeleportService pendingTeleportService();

    TeleportCooldownService cooldownService();

    TeleportOptionsFactory options();

    PaperTaskScheduler scheduler();

    @Override
    void close();
}

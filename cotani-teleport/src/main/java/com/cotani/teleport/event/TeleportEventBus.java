package com.cotani.teleport.event;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

public final class TeleportEventBus {
    private final PaperTaskScheduler scheduler;

    public TeleportEventBus(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public CompletableFuture<Void> callAsync(Event event) {
        Objects.requireNonNull(event, "event");
        if (Bukkit.isPrimaryThread()) {
            call(event);
            return CompletableFuture.completedFuture(null);
        }
        return scheduler.supply(ExecutionTarget.global(), "teleport-event", () -> {
            call(event);
            return null;
        });
    }

    public void call(Event event) {
        Objects.requireNonNull(event, "event");
        Bukkit.getPluginManager().callEvent(event);
    }
}

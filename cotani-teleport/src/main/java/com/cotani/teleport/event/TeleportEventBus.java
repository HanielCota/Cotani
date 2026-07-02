package com.cotani.teleport.event;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;

public final class TeleportEventBus {
    private final PaperTaskScheduler scheduler;

    public TeleportEventBus(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public CompletionStage<Void> callAsync(Event event, Entity owner) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(owner, "owner");
        return scheduler.supply(ExecutionTarget.entity(owner), "teleport-event", () -> {
            call(event);
            return null;
        });
    }

    public CompletionStage<Void> callAsync(Event event) {
        Objects.requireNonNull(event, "event");
        return scheduler.supply(ExecutionTarget.global(), "teleport-event", () -> {
            call(event);
            return null;
        });
    }

    public void call(Event event) {
        Objects.requireNonNull(event, "event");
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
    }
}

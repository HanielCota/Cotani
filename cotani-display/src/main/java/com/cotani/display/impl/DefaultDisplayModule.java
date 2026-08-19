package com.cotani.display.impl;

import com.cotani.api.InternalApi;
import com.cotani.display.api.DisplayModule;
import com.cotani.display.api.HologramService;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

@InternalApi
public final class DefaultDisplayModule implements DisplayModule {

    private final DefaultHologramService hologramService;
    private final DisplayInteractionListener listener;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultDisplayModule(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.hologramService = new DefaultHologramService(scheduler);
        this.listener = new DisplayInteractionListener(hologramService);

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @Override
    public HologramService holograms() {
        return hologramService;
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        if (!closed.compareAndSet(false, true)) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        HandlerList.unregisterAll(listener);
        listener.clear();
        return hologramService.clearAsync();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        HandlerList.unregisterAll(listener);
        listener.clear();
        hologramService.clearAsync();
    }
}

package com.cotani.display.impl;

import com.cotani.api.InternalApi;
import com.cotani.display.api.DisplayModule;
import com.cotani.display.api.HologramService;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultDisplayModule implements DisplayModule {

    private final DefaultHologramService hologramService;
    private final DisplayInteractionListener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<@Nullable CompletableFuture<Void>> closeFuture = new AtomicReference<>();

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
        var existing = closeFuture.get();
        if (existing != null) {
            return existing;
        }

        var promise = new CompletableFuture<Void>();
        if (!closeFuture.compareAndSet(null, promise)) {
            return Objects.requireNonNull(closeFuture.get());
        }

        closed.set(true);
        HandlerList.unregisterAll(listener);
        listener.clear();
        hologramService.clearAsync().whenComplete((_, error) -> {
            if (error != null) {
                promise.completeExceptionally(error);
                return;
            }
            promise.complete(null);
        });
        return promise;
    }

    @Override
    public void close() {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DisplayModule.close() blocks; use closeAsync() on the server thread.");
        }
        try {
            closeAsync().toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("DisplayModule.close() failed", failure);
        }
    }
}

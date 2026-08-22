package com.cotani.npc.impl;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import com.cotani.npc.api.NpcEquipment;
import com.cotani.npc.api.NpcModule;
import com.cotani.npc.api.NpcSkin;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * Default implementation of {@link NpcModule}.
 */
@InternalApi
public final class DefaultNpcModule implements NpcModule {

    private final PaperTaskScheduler scheduler;
    private final NpcRegistry registry;
    private final NpcRenderer renderer;
    private final NpcTracker tracker;
    private final NpcPlayerListener listener;
    private final SchedulerTask trackingTask;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultNpcModule(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        this.registry = new NpcRegistry();
        this.renderer = new NpcRenderer();
        this.tracker = new NpcTracker(renderer);

        this.listener = new NpcPlayerListener(this);
        var server = plugin.getServer();
        if (server != null) {
            server.getPluginManager().registerEvents(listener, plugin);
        }

        // Periodic tracking loop (every 100ms / 2 ticks) to update distances and look-at
        this.trackingTask = scheduler.asyncTimer(this::tickTracking, Duration.ofMillis(100), Duration.ofMillis(100));
    }

    NpcRegistry registry() {
        return registry;
    }

    @Override
    public Npc create(Location location, String name) {
        Objects.requireNonNull(location, "Parameter 'location' must not be null");
        Objects.requireNonNull(name, "Parameter 'name' must not be null");

        var npc = Npc.builder().location(location).name(name).build();
        spawn(npc);
        return npc;
    }

    @Override
    public Npc create(Consumer<Npc.Builder> builderConsumer) {
        Objects.requireNonNull(builderConsumer, "Parameter 'builderConsumer' must not be null");

        var builder = Npc.builder();
        builderConsumer.accept(builder);
        var npc = builder.build();
        spawn(npc);
        return npc;
    }

    @Override
    public void spawn(Npc npc) {
        Objects.requireNonNull(npc, "Parameter 'npc' must not be null");
        if (closed.get()) {
            return;
        }

        registry.register(npc);
        refresh(npc);
    }

    @Override
    public void despawn(Npc npc) {
        Objects.requireNonNull(npc, "Parameter 'npc' must not be null");
        despawn(npc.id());
    }

    @Override
    public void despawn(UUID npcId) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        if (closed.get()) {
            return;
        }

        var optNpc = registry.unregister(npcId);
        if (optNpc.isEmpty()) {
            return;
        }

        var npc = optNpc.get();
        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        for (var viewer : List.copyOf(server.getOnlinePlayers())) {
            scheduler.entity(viewer.getUniqueId(), () -> {
                if (closed.get()) {
                    return;
                }
                var currentViewer = Bukkit.getPlayer(viewer.getUniqueId());
                if (currentViewer != null && currentViewer.isOnline()) {
                    renderer.renderDespawn(currentViewer, npc);
                }
            });
        }
    }

    @Override
    public Optional<Npc> findNpc(UUID npcId) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        return registry.find(npcId);
    }

    @Override
    public Collection<Npc> allNpcs() {
        return registry.all();
    }

    @Override
    public void updateLocation(UUID npcId, Location newLocation) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        Objects.requireNonNull(newLocation, "Parameter 'newLocation' must not be null");

        if (closed.get()) {
            return;
        }

        var optNpc = registry.find(npcId);
        if (optNpc.isPresent()) {
            var updated = optNpc.get().toBuilder().location(newLocation).build();
            registry.update(updated);
            refresh(updated);
        }
    }

    @Override
    public void updateSkin(UUID npcId, NpcSkin skin) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        Objects.requireNonNull(skin, "Parameter 'skin' must not be null");

        if (closed.get()) {
            return;
        }

        var optNpc = registry.find(npcId);
        if (optNpc.isPresent()) {
            var updated = optNpc.get().toBuilder().skin(skin).build();
            registry.update(updated);
            refresh(updated);
        }
    }

    @Override
    public void updateEquipment(UUID npcId, NpcEquipment equipment) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");
        Objects.requireNonNull(equipment, "Parameter 'equipment' must not be null");

        if (closed.get()) {
            return;
        }

        var optNpc = registry.find(npcId);
        if (optNpc.isPresent()) {
            var updated = optNpc.get().toBuilder().equipment(equipment).build();
            registry.update(updated);

            var server = Bukkit.getServer();
            if (server != null) {
                for (var viewer : List.copyOf(server.getOnlinePlayers())) {
                    scheduler.entity(viewer.getUniqueId(), () -> {
                        if (closed.get()) {
                            return;
                        }
                        var currentViewer = Bukkit.getPlayer(viewer.getUniqueId());
                        if (currentViewer != null && currentViewer.isOnline()) {
                            renderer.renderEquipment(currentViewer, updated);
                        }
                    });
                }
            }
        }
    }

    @Override
    public void refresh(Npc npc) {
        Objects.requireNonNull(npc, "Parameter 'npc' must not be null");
        if (closed.get()) {
            return;
        }

        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        var npcs = List.of(npc);
        for (var viewer : List.copyOf(server.getOnlinePlayers())) {
            var viewerId = viewer.getUniqueId();
            scheduler.entity(viewerId, () -> {
                if (closed.get()) {
                    return;
                }
                var currentViewer = Bukkit.getPlayer(viewerId);
                if (currentViewer != null && currentViewer.isOnline()) {
                    tracker.trackViewer(currentViewer, npcs);
                }
            });
        }
    }

    @Override
    public void refreshAll() {
        if (closed.get()) {
            return;
        }

        var npcs = List.copyOf(registry.all());
        if (npcs.isEmpty()) {
            return;
        }

        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        for (var viewer : List.copyOf(server.getOnlinePlayers())) {
            var viewerId = viewer.getUniqueId();
            scheduler.entity(viewerId, () -> {
                if (closed.get()) {
                    return;
                }
                var currentViewer = Bukkit.getPlayer(viewerId);
                if (currentViewer != null && currentViewer.isOnline()) {
                    tracker.trackViewer(currentViewer, npcs);
                }
            });
        }
    }

    public void handlePlayerQuit(Player quittingPlayer) {
        renderer.removeViewer(quittingPlayer.getUniqueId());
    }

    private void tickTracking() {
        if (closed.get()) {
            return;
        }

        var npcs = List.copyOf(registry.all());
        if (npcs.isEmpty()) {
            return;
        }

        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        for (var viewer : List.copyOf(server.getOnlinePlayers())) {
            var viewerId = viewer.getUniqueId();
            scheduler.entity(viewerId, () -> {
                if (closed.get()) {
                    return;
                }
                var currentViewer = Bukkit.getPlayer(viewerId);
                if (currentViewer != null && currentViewer.isOnline()) {
                    tracker.trackViewer(currentViewer, npcs);
                }
            });
        }
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        trackingTask.cancel();
        HandlerList.unregisterAll(listener);

        var npcs = List.copyOf(registry.all());
        registry.clear();

        var server = Bukkit.getServer();
        if (server == null || server.getOnlinePlayers().isEmpty() || npcs.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        var futures = new ArrayList<CompletableFuture<Void>>();
        for (var viewer : List.copyOf(server.getOnlinePlayers())) {
            var viewerId = viewer.getUniqueId();
            var cleanupFuture = new CompletableFuture<Void>();
            futures.add(cleanupFuture);

            scheduler.entity(viewerId, () -> {
                try {
                    var player = Bukkit.getPlayer(viewerId);
                    if (player != null && player.isOnline()) {
                        renderer.clearAllForViewer(player, npcs);
                    }
                    cleanupFuture.complete(null);
                } catch (Throwable t) {
                    cleanupFuture.completeExceptionally(t);
                }
            });
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public void close() {
        closeAsync();
    }
}

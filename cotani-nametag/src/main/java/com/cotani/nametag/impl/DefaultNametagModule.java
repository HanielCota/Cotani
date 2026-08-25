package com.cotani.nametag.impl;

import com.cotani.api.InternalApi;
import com.cotani.nametag.api.Nametag;
import com.cotani.nametag.api.NametagModule;
import com.cotani.nametag.api.NametagProvider;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * Default implementation of {@link NametagModule}.
 *
 * <p>Orchestrates nametag updates across Folia/Paper threads via {@link PaperTaskScheduler},
 * delegating state management to {@link NametagRegistry} and scoreboard manipulation to {@link NametagTeamRenderer}.
 */
@InternalApi
public final class DefaultNametagModule implements NametagModule {

    private static final String PLAYER_NULL_MSG = "Parameter 'player' must not be null";
    private static final String PLAYER_ID_NULL_MSG = "Parameter 'playerId' must not be null";
    private static final String VIEWER_NULL_MSG = "Parameter 'viewer' must not be null";
    private static final String TARGET_NULL_MSG = "Parameter 'target' must not be null";
    private static final String VIEWER_ID_NULL_MSG = "Parameter 'viewerId' must not be null";
    private static final String TARGET_ID_NULL_MSG = "Parameter 'targetId' must not be null";
    private static final String NAMETAG_NULL_MSG = "Parameter 'nametag' must not be null";

    private final PaperTaskScheduler scheduler;
    private final NametagRegistry registry;
    private final NametagPlayerListener listener;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultNametagModule(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        this.registry = new NametagRegistry();

        this.listener = new NametagPlayerListener(this);
        var server = plugin.getServer();
        if (server != null) {
            server.getPluginManager().registerEvents(listener, plugin);
        }
    }

    @Override
    public void apply(Player player, Nametag nametag) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        apply(player.getUniqueId(), nametag);
    }

    @Override
    public void apply(UUID playerId, Nametag nametag) {
        Objects.requireNonNull(playerId, PLAYER_ID_NULL_MSG);
        Objects.requireNonNull(nametag, NAMETAG_NULL_MSG);

        if (closed.get()) {
            return;
        }

        registry.setGlobal(playerId, nametag);
        dispatchTargetUpdate(playerId);
    }

    @Override
    public void applyBatch(Map<UUID, Nametag> batch) {
        Objects.requireNonNull(batch, "Parameter 'batch' must not be null");

        if (closed.get() || batch.isEmpty()) {
            return;
        }

        var targetsToUpdate = new ArrayList<UUID>(batch.size());
        for (var entry : batch.entrySet()) {
            var playerId = Objects.requireNonNull(entry.getKey(), PLAYER_ID_NULL_MSG);
            var nametag = Objects.requireNonNull(entry.getValue(), NAMETAG_NULL_MSG);

            registry.setGlobal(playerId, nametag);
            targetsToUpdate.add(playerId);
        }

        dispatchBatchUpdate(targetsToUpdate);
    }

    @Override
    public void applyForViewer(Player viewer, Player target, Nametag nametag) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(target, TARGET_NULL_MSG);
        applyForViewer(viewer.getUniqueId(), target.getUniqueId(), nametag);
    }

    @Override
    public void applyForViewer(UUID viewerId, UUID targetId, Nametag nametag) {
        Objects.requireNonNull(viewerId, VIEWER_ID_NULL_MSG);
        Objects.requireNonNull(targetId, TARGET_ID_NULL_MSG);
        Objects.requireNonNull(nametag, NAMETAG_NULL_MSG);

        if (closed.get()) {
            return;
        }

        registry.setOverride(viewerId, targetId, nametag);
        scheduler.entity(viewerId, () -> {
            if (closed.get()) {
                return;
            }
            var server = Bukkit.getServer();
            if (server == null) {
                return;
            }
            var viewer = server.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                renderForViewer(viewer, targetId);
            }
        });
    }

    @Override
    public void reset(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        reset(player.getUniqueId());
    }

    @Override
    public void reset(UUID playerId) {
        Objects.requireNonNull(playerId, PLAYER_ID_NULL_MSG);

        if (closed.get()) {
            return;
        }

        registry.removeGlobal(playerId);
        dispatchTargetUpdate(playerId);
    }

    @Override
    public void resetBatch(Collection<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "Parameter 'playerIds' must not be null");

        if (closed.get() || playerIds.isEmpty()) {
            return;
        }

        var targetsToUpdate = new ArrayList<UUID>(playerIds.size());
        for (var playerId : playerIds) {
            if (playerId != null) {
                registry.removeGlobal(playerId);
                targetsToUpdate.add(playerId);
            }
        }

        dispatchBatchUpdate(targetsToUpdate);
    }

    @Override
    public void resetForViewer(Player viewer, Player target) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(target, TARGET_NULL_MSG);
        resetForViewer(viewer.getUniqueId(), target.getUniqueId());
    }

    @Override
    public void resetForViewer(UUID viewerId, UUID targetId) {
        Objects.requireNonNull(viewerId, VIEWER_ID_NULL_MSG);
        Objects.requireNonNull(targetId, TARGET_ID_NULL_MSG);

        if (closed.get()) {
            return;
        }

        registry.removeOverride(viewerId, targetId);
        scheduler.entity(viewerId, () -> {
            if (closed.get()) {
                return;
            }
            var server = Bukkit.getServer();
            if (server == null) {
                return;
            }
            var viewer = server.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                renderForViewer(viewer, targetId);
            }
        });
    }

    @Override
    public void resetAll(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        resetAll(player.getUniqueId());
    }

    @Override
    public void resetAll(UUID playerId) {
        Objects.requireNonNull(playerId, PLAYER_ID_NULL_MSG);

        if (closed.get()) {
            return;
        }

        registry.removeAll(playerId);
        refresh(playerId);
    }

    @Override
    public Optional<Nametag> getNametag(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        return getNametag(player.getUniqueId());
    }

    @Override
    public Optional<Nametag> getNametag(UUID playerId) {
        Objects.requireNonNull(playerId, PLAYER_ID_NULL_MSG);
        return registry.getGlobal(playerId);
    }

    @Override
    public Nametag getEffectiveNametag(Player viewer, Player target) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(target, TARGET_NULL_MSG);
        return registry.resolveEffective(viewer, target);
    }

    @Override
    public Nametag getEffectiveNametag(UUID viewerId, UUID targetId) {
        Objects.requireNonNull(viewerId, VIEWER_ID_NULL_MSG);
        Objects.requireNonNull(targetId, TARGET_ID_NULL_MSG);
        return registry.resolveEffective(viewerId, targetId);
    }

    @Override
    public Map<UUID, Nametag> globalNametags() {
        return registry.globalTagsCopy();
    }

    @Override
    public void registerProvider(NametagProvider provider) {
        Objects.requireNonNull(provider, "Parameter 'provider' must not be null");
        registry.registerProvider(provider);
        refreshAll();
    }

    @Override
    public void unregisterProvider(NametagProvider provider) {
        Objects.requireNonNull(provider, "Parameter 'provider' must not be null");
        registry.unregisterProvider(provider);
        refreshAll();
    }

    @Override
    public void refreshAll() {
        if (closed.get()) {
            return;
        }

        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        var onlineSnapshot = List.copyOf(server.getOnlinePlayers());
        for (var viewer : onlineSnapshot) {
            var viewerId = viewer.getUniqueId();
            scheduler.entity(viewerId, () -> {
                if (closed.get()) {
                    return;
                }
                var currentViewer = Bukkit.getPlayer(viewerId);
                if (currentViewer == null || !currentViewer.isOnline()) {
                    return;
                }
                for (var target : List.copyOf(Bukkit.getOnlinePlayers())) {
                    var effective = registry.resolveEffective(currentViewer, target);
                    NametagTeamRenderer.renderTeam(currentViewer, target, effective);
                }
            });
        }
    }

    @Override
    public void refresh(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        refresh(player.getUniqueId());
    }

    @Override
    public void refresh(UUID playerId) {
        Objects.requireNonNull(playerId, PLAYER_ID_NULL_MSG);

        if (closed.get()) {
            return;
        }

        // Refresh player as viewer
        scheduler.entity(playerId, () -> {
            if (closed.get()) {
                return;
            }
            var server = Bukkit.getServer();
            if (server == null) {
                return;
            }
            var viewer = server.getPlayer(playerId);
            if (viewer == null || !viewer.isOnline()) {
                return;
            }
            for (var target : List.copyOf(server.getOnlinePlayers())) {
                var effective = registry.resolveEffective(viewer, target);
                NametagTeamRenderer.renderTeam(viewer, target, effective);
            }
        });

        // Refresh player as target for all online viewers
        dispatchTargetUpdate(playerId);
    }

    public void handlePlayerJoin(Player joinedPlayer) {
        if (closed.get()) {
            return;
        }

        var joinedId = joinedPlayer.getUniqueId();

        // On joined player's thread: render all existing players for them
        scheduler.entity(joinedId, () -> {
            if (closed.get()) {
                return;
            }
            var viewer = Bukkit.getPlayer(joinedId);
            if (viewer == null || !viewer.isOnline()) {
                return;
            }
            for (var target : List.copyOf(Bukkit.getOnlinePlayers())) {
                var effective = registry.resolveEffective(viewer, target);
                NametagTeamRenderer.renderTeam(viewer, target, effective);
            }
        });

        // On every other online player's thread: render joined player for them
        var server = Bukkit.getServer();
        if (server != null) {
            for (var other : List.copyOf(server.getOnlinePlayers())) {
                if (other.getUniqueId().equals(joinedId)) {
                    continue;
                }
                var otherId = other.getUniqueId();
                scheduler.entity(otherId, () -> {
                    if (closed.get()) {
                        return;
                    }
                    var otherViewer = Bukkit.getPlayer(otherId);
                    var target = Bukkit.getPlayer(joinedId);
                    if (otherViewer != null && otherViewer.isOnline() && target != null && target.isOnline()) {
                        var effective = registry.resolveEffective(otherViewer, target);
                        NametagTeamRenderer.renderTeam(otherViewer, target, effective);
                    }
                });
            }
        }
    }

    public void handlePlayerQuit(Player quittingPlayer) {
        var quittingId = quittingPlayer.getUniqueId();
        var quittingName = quittingPlayer.getName();

        registry.removeAll(quittingId);

        var server = Bukkit.getServer();
        if (server != null) {
            for (var other : List.copyOf(server.getOnlinePlayers())) {
                if (other.getUniqueId().equals(quittingId)) {
                    continue;
                }
                var otherId = other.getUniqueId();
                scheduler.entity(otherId, () -> {
                    var otherViewer = Bukkit.getPlayer(otherId);
                    if (otherViewer != null && otherViewer.isOnline()) {
                        NametagTeamRenderer.removeTarget(otherViewer, quittingName);
                    }
                });
            }
        }
    }

    public void handlePlayerRefresh(Player player) {
        refresh(player);
    }

    private void dispatchTargetUpdate(UUID targetId) {
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
                    renderForViewer(currentViewer, targetId);
                }
            });
        }
    }

    private void dispatchBatchUpdate(Collection<UUID> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }

        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        var targetsCopy = List.copyOf(targetIds);
        for (var viewer : List.copyOf(server.getOnlinePlayers())) {
            var viewerId = viewer.getUniqueId();
            scheduler.entity(viewerId, () -> {
                if (closed.get()) {
                    return;
                }
                var currentViewer = Bukkit.getPlayer(viewerId);
                if (currentViewer != null && currentViewer.isOnline()) {
                    for (var targetId : targetsCopy) {
                        renderForViewer(currentViewer, targetId);
                    }
                }
            });
        }
    }

    private void renderForViewer(Player viewer, UUID targetId) {
        var server = Bukkit.getServer();
        if (server == null) {
            return;
        }

        var target = server.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            return;
        }

        var effective = registry.resolveEffective(viewer, target);
        NametagTeamRenderer.renderTeam(viewer, target, effective);
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        if (!closed.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        HandlerList.unregisterAll(listener);

        var server = Bukkit.getServer();
        if (server == null || server.getOnlinePlayers().isEmpty()) {
            registry.clear();
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
                        NametagTeamRenderer.clearAllTeams(player);
                    }
                    cleanupFuture.complete(null);
                } catch (Exception t) {
                    cleanupFuture.completeExceptionally(t);
                }
            });
        }

        registry.clear();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public void close() {
        closeAsync().whenComplete((_, error) -> {
            if (error != null) {
                java.util.logging.Logger.getLogger(DefaultNametagModule.class.getName())
                        .log(java.util.logging.Level.SEVERE, "Failed to close nametag module", error);
            }
        });
    }
}

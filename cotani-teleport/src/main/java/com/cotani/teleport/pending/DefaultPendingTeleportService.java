package com.cotani.teleport.pending;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.PendingTeleportService;
import com.cotani.teleport.api.PendingTeleportView;
import com.cotani.teleport.api.TeleportCancelReason;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import com.cotani.teleport.api.TeleportRequest;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.api.TeleportService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class DefaultPendingTeleportService implements PendingTeleportService, AutoCloseable {
    private final TeleportService teleportService;
    private final PaperTaskScheduler scheduler;
    private final Map<UUID, PendingTeleportStateMachine> pendingByPlayer = new ConcurrentHashMap<>();

    public DefaultPendingTeleportService(TeleportService teleportService, PaperTaskScheduler scheduler) {
        this.teleportService = teleportService;
        this.scheduler = scheduler;
    }

    @Override
    public UUID schedule(
            Player player,
            Location target,
            Duration delay,
            TeleportOptions options,
            TeleportCause cause,
            String source) {
        UUID playerId = player.getUniqueId();
        PendingTeleportData data = PendingTeleportData.create(playerId, target, delay, options, cause, source);
        PendingTeleportStateMachine pending = new PendingTeleportStateMachine(data);

        PendingTeleportStateMachine previous = pendingByPlayer.put(playerId, pending);
        if (previous != null) {
            previous.cancel(TeleportCancelReason.REPLACED);
        }

        pending.attachTask(
                scheduler.entityLater("pending-teleport-" + data.id(), player, () -> execute(pending), delay));
        return data.id();
    }

    @Override
    public boolean cancel(Player player, TeleportCancelReason reason) {
        PendingTeleportStateMachine pending = pendingByPlayer.remove(player.getUniqueId());
        return pending != null && pending.cancel(reason);
    }

    @Override
    public boolean hasPending(Player player) {
        return pendingByPlayer.containsKey(player.getUniqueId());
    }

    @Override
    public Optional<PendingTeleportView> find(Player player) {
        return Optional.ofNullable(pendingByPlayer.get(player.getUniqueId())).map(this::toView);
    }

    @Override
    public void close() {
        pendingByPlayer.values().forEach(pending -> pending.cancel(TeleportCancelReason.QUIT));
        pendingByPlayer.clear();
    }

    private void execute(PendingTeleportStateMachine pending) {
        if (!pending.markExecuting()) {
            return;
        }

        PendingTeleportData data = pending.data();
        Player player = Bukkit.getPlayer(data.playerId());
        if (player == null || !player.isOnline()) {
            pendingByPlayer.remove(data.playerId(), pending);
            pending.cancelExecution(TeleportCancelReason.QUIT);
            return;
        }

        TeleportRequest request = TeleportRequest.builder()
                .player(player)
                .target(data.target())
                .cause(data.cause())
                .source(data.source())
                .options(data.options())
                .build();

        var _ = teleportService.teleport(request).whenComplete((result, error) -> {
            pendingByPlayer.remove(data.playerId(), pending);
            if (error != null) {
                pending.cancelExecution(TeleportCancelReason.EXECUTION_FAILED);
                return;
            }
            if (result instanceof TeleportResult.Success) {
                pending.markCompleted();
                return;
            }
            pending.cancelExecution(TeleportCancelReason.EXECUTION_FAILED);
        });
    }

    private PendingTeleportView toView(PendingTeleportStateMachine pending) {
        PendingTeleportData data = pending.data();
        return new PendingTeleportView(
                data.id(), data.playerId(), data.target(), data.delay(), pending.state(), pending.cancelReason());
    }
}

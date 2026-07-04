package com.cotani.teleport.pending;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.*;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
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
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public UUID schedule(
            UUID playerId,
            Location target,
            Duration delay,
            TeleportOptions options,
            TeleportCause cause,
            String source) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(source, "source");
        PendingTeleportData data = PendingTeleportData.create(playerId, target, delay, options, cause, source);
        PendingTeleportStateMachine pending = new PendingTeleportStateMachine(data);

        PendingTeleportStateMachine previous = pendingByPlayer.put(playerId, pending);
        if (previous != null) {
            if (!previous.cancel(TeleportCancelReason.REPLACED)) {
                previous.cancelExecution(TeleportCancelReason.REPLACED);
            }
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            pendingByPlayer.remove(playerId, pending);
            pending.cancelExecution(TeleportCancelReason.QUIT);
            return data.id();
        }
        pending.attachTask(
                scheduler.entityLater("pending-teleport-" + data.id(), player, () -> execute(pending), delay));
        return data.id();
    }

    @Override
    public boolean cancel(UUID playerId, TeleportCancelReason reason) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reason, "reason");
        PendingTeleportStateMachine pending = pendingByPlayer.remove(playerId);
        return pending != null && pending.cancel(reason);
    }

    @Override
    public boolean hasPending(UUID playerId) {
        return pendingByPlayer.containsKey(playerId);
    }

    @Override
    public Optional<PendingTeleportView> find(UUID playerId) {
        return Optional.ofNullable(pendingByPlayer.get(playerId)).map(this::toView);
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
                .playerId(data.playerId())
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

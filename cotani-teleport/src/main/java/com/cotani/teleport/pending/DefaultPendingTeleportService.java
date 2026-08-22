package com.cotani.teleport.pending;

import com.cotani.api.InternalApi;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.PendingTeleportService;
import com.cotani.teleport.api.PendingTeleportView;
import com.cotani.teleport.api.PlayerResolver;
import com.cotani.teleport.api.TeleportCancelReason;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import com.cotani.teleport.api.TeleportRequest;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.api.TeleportService;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@InternalApi
public final class DefaultPendingTeleportService implements PendingTeleportService, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DefaultPendingTeleportService.class.getName());

    private final TeleportService teleportService;
    private final PaperTaskScheduler scheduler;
    private final PlayerResolver playerResolver;
    private final Map<UUID, PendingTeleportStateMachine> pendingByPlayer = new ConcurrentHashMap<>();

    public DefaultPendingTeleportService(TeleportService teleportService, PaperTaskScheduler scheduler) {
        this(teleportService, scheduler, PlayerResolver.bukkit());
    }

    public DefaultPendingTeleportService(
            TeleportService teleportService, PaperTaskScheduler scheduler, PlayerResolver playerResolver) {
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
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
            previous.cancel(TeleportCancelReason.REPLACED);
        }

        Player player = playerResolver.resolve(playerId);

        if (player == null) {
            pendingByPlayer.remove(playerId, pending);
            pending.cancel(TeleportCancelReason.QUIT);

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

        PendingTeleportStateMachine pending = pendingByPlayer.get(playerId);

        if (pending == null) {
            return false;
        }

        boolean cancelled = pending.cancel(reason);

        if (cancelled) {
            pendingByPlayer.remove(playerId, pending);
        }

        return cancelled;
    }

    @Override
    public boolean hasPending(UUID playerId) {
        PendingTeleportStateMachine pending = pendingByPlayer.get(playerId);
        return pending != null && !pending.isCancelled();
    }

    @Override
    public Optional<PendingTeleportView> find(UUID playerId) {
        return Optional.ofNullable(pendingByPlayer.get(playerId))
                .filter(pending -> !pending.isCancelled())
                .map(this::toView);
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
        // Re-check after claiming EXECUTING: damage/move may have cancelled in the gap.
        if (pending.isCancelled()) {
            pendingByPlayer.remove(data.playerId(), pending);
            return;
        }

        Player player = playerResolver.resolve(data.playerId());

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
                .abortIf(pending::isCancelled)
                .build();

        // Final gate immediately before starting the teleport pipeline.
        if (pending.isCancelled()) {
            pendingByPlayer.remove(data.playerId(), pending);
            return;
        }

        var _ = teleportService.teleport(request).whenComplete((result, error) -> {
            pendingByPlayer.remove(data.playerId(), pending);
            if (pending.isCancelled()) {
                return;
            }
            if (error != null) {
                LOGGER.log(
                        Level.WARNING,
                        error,
                        () -> "Pending teleport execution failed. playerId=" + data.playerId() + " requestId="
                                + data.id());
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
                data.id(),
                data.playerId(),
                data.target(),
                data.delay(),
                pending.state(),
                pending.cancelReason().orElse(null));
    }
}

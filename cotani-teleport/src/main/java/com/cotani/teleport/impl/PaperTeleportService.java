package com.cotani.teleport.impl;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.*;
import com.cotani.teleport.event.CotaniPreTeleportEvent;
import com.cotani.teleport.policy.PolicyResult;
import com.cotani.teleport.policy.TeleportCooldownService;
import com.cotani.teleport.policy.TeleportPolicyChain;
import com.cotani.teleport.safety.SafeLocationResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * Paper-backed teleport service.
 *
 * <p>The pipeline is kept intentionally coarse-grained to avoid scheduler hop overhead: safe-location
 * resolution runs on the region thread, then policy validation runs in a single entity task. If the
 * policy allows the teleport, the pre-teleport event is fired synchronously on the same entity thread
 * and the teleport is executed in one continuation.
 */
@SuppressWarnings("resource")
public final class PaperTeleportService implements com.cotani.teleport.api.TeleportService {

    private final Dependencies deps;

    public PaperTeleportService(Dependencies deps) {
        this.deps = Objects.requireNonNull(deps, "deps");
    }

    @Override
    public CompletionStage<TeleportResult> teleport(TeleportRequest request) {
        Objects.requireNonNull(request, "request");

        Location target = request.target();
        ExecutionTarget startTarget =
                target.getWorld() != null ? ExecutionTarget.region(target) : ExecutionTarget.global();

        return deps.scheduler()
                .supply(startTarget, "teleport-prepare", () -> prepare(request))
                .thenCompose(this::resolveAndFinish);
    }

    private PreparedTeleport prepare(TeleportRequest request) {
        Player player = resolvePlayer(request.playerId());
        Location originalTarget = request.target().clone();
        TeleportOptions options = request.options();
        if (player == null) {
            TeleportContext context = new TeleportContext(
                    request.playerId(),
                    originalTarget.clone(),
                    originalTarget,
                    request.cause(),
                    options,
                    request.source(),
                    Instant.now(deps.clock()));
            return new PreparedTeleport(
                    context,
                    originalTarget,
                    Optional.of(TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE)));
        }
        Location from =
                Objects.requireNonNull(player.getLocation(), "player.location").clone();
        TeleportContext context = new TeleportContext(
                request.playerId(),
                from,
                originalTarget,
                request.cause(),
                options,
                request.source(),
                Instant.now(deps.clock()));
        return new PreparedTeleport(context, originalTarget, TeleportValidator.validateInitial(context));
    }

    private CompletionStage<TeleportResult> resolveAndFinish(PreparedTeleport prepared) {
        TeleportContext context = prepared.context();
        Location originalTarget = prepared.originalTarget();

        if (prepared.initialFailure().isPresent()) {
            return notifyFailure(prepared.initialFailure().get());
        }

        if (!context.options().safeLocation()) {
            return finishTeleport(context, originalTarget);
        }

        return deps.safeLocationResolver()
                .resolve(originalTarget, context.options().safeLocationOptions())
                .thenCompose(targetResult -> targetResult
                        .map(resolved -> finishTeleport(context.withTarget(resolved), resolved))
                        .orElseGet(() -> notifyFailure(
                                TeleportResults.failure(context, TeleportFailureReason.UNSAFE_LOCATION))));
    }

    private CompletionStage<TeleportResult> finishTeleport(TeleportContext context, Location resolvedTarget) {
        return deps.scheduler()
                .supply(ExecutionTarget.entity(context.playerId()), "teleport-finish", () -> validateOrStart(context))
                .thenCompose(result -> result.<CompletionStage<TeleportResult>>map(CompletableFuture::completedFuture)
                        .orElseGet(() -> firePreTeleportAndExecute(context, resolvedTarget)));
    }

    private Optional<TeleportResult.Failure> validateOrStart(TeleportContext context) {
        Player player = resolvePlayer(context.playerId());
        if (player == null) {
            return Optional.of(TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }

        PolicyResult policyResult = deps.policyChain().validate(context);
        if (!(policyResult instanceof PolicyResult.Denied denied)) {
            return Optional.empty();
        }

        if (context.options().sendMessages()) {
            player.sendMessage(denied.message());
        }

        return Optional.of(TeleportResults.failure(context, denied.reason()));
    }

    private CompletionStage<TeleportResult> firePreTeleportAndExecute(
            TeleportContext context, Location resolvedTarget) {
        Player player = resolvePlayer(context.playerId());
        if (player == null) {
            return CompletableFuture.completedFuture(
                    TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }

        CotaniPreTeleportEvent event = deps.eventNotifier()
                .firePreTeleportSync(player, context.from(), resolvedTarget, context.cause(), context.source());

        if (event.isCancelled()) {
            return notifyFailure(TeleportResults.failure(context, TeleportFailureReason.CANCELLED_BY_EVENT));
        }

        Location eventTarget =
                Objects.requireNonNull(event.getTo(), "preEvent.to").clone();
        return executeTeleport(context, player, eventTarget);
    }

    private CompletionStage<TeleportResult> executeTeleport(
            TeleportContext context, Player player, Location eventTarget) {
        TeleportOptions options = context.options();
        Instant startedAt = Instant.now(deps.clock());

        preparePlayer(player, options.player());
        Vector velocity = player.getVelocity();

        CompletableFuture<Boolean> teleportFuture = startTeleport(player, eventTarget, options);

        return teleportFuture
                .orTimeout(options.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenCompose(success -> {
                    if (!success) {
                        return deps.resultMapper().mapTeleportFailure(context);
                    }

                    applyCooldown(context);

                    if (options.preserveVelocity()) {
                        player.setVelocity(velocity);
                    }
                    return deps.resultMapper().mapSuccess(context, context.from(), eventTarget, startedAt);
                })
                .exceptionallyCompose(error -> deps.resultMapper().mapException(context, error));
    }

    private CompletableFuture<Boolean> startTeleport(Player player, Location eventTarget, TeleportOptions options) {
        if (options.async()) {
            return player.teleportAsync(eventTarget);
        }
        return CompletableFuture.completedFuture(player.teleport(eventTarget));
    }

    private CompletionStage<TeleportResult> notifyFailure(TeleportResult.Failure failure) {
        return deps.eventNotifier().fireFailure(failure).thenApply(_ -> failure);
    }

    private void preparePlayer(Player player, PlayerSettings settings) {
        if (settings.dismount()) {
            player.leaveVehicle();
        }
        if (settings.closeInventory()) {
            player.closeInventory();
        }
    }

    private void applyCooldown(TeleportContext context) {
        TeleportOptions options = context.options();
        if (options.checkCooldown()) {
            deps.cooldownService().put(context.playerId(), context.cause(), options.cooldownDuration());
        }
    }

    private @Nullable Player resolvePlayer(UUID playerId) {
        return org.bukkit.Bukkit.getPlayer(playerId);
    }

    record Dependencies(
            TeleportPolicyChain policyChain,
            SafeLocationResolver safeLocationResolver,
            TeleportEventNotifier eventNotifier,
            TeleportResultMapper resultMapper,
            TeleportCooldownService cooldownService,
            PaperTaskScheduler scheduler,
            Clock clock) {
        Dependencies {
            Objects.requireNonNull(policyChain, "policyChain");
            Objects.requireNonNull(safeLocationResolver, "safeLocationResolver");
            Objects.requireNonNull(eventNotifier, "eventNotifier");
            Objects.requireNonNull(resultMapper, "resultMapper");
            Objects.requireNonNull(cooldownService, "cooldownService");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(clock, "clock");
        }
    }

    private record PreparedTeleport(
            TeleportContext context, Location originalTarget, Optional<TeleportResult.Failure> initialFailure) {}
}

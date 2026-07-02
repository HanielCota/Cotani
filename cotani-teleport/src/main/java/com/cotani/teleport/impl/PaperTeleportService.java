package com.cotani.teleport.impl;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.*;
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

public final class PaperTeleportService implements com.cotani.teleport.api.TeleportService {

    private final Dependencies deps;

    public PaperTeleportService(Dependencies deps) {
        this.deps = deps;
    }

    record Dependencies(
            TeleportPolicyChain policyChain,
            SafeLocationResolver safeLocationResolver,
            TeleportEventNotifier eventNotifier,
            TeleportResultMapper resultMapper,
            TeleportCooldownService cooldownService,
            PaperTaskScheduler scheduler,
            Clock clock) {}

    private record PreparedTeleport(
            TeleportContext context, Location originalTarget, Optional<TeleportResult.Failure> initialFailure) {}

    @Override
    public CompletionStage<TeleportResult> teleport(TeleportRequest request) {
        Objects.requireNonNull(request, "request");

        return deps.scheduler()
                .supply(ExecutionTarget.global(), "teleport-resolve", () -> prepare(request))
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
        TeleportOptions options = context.options();

        if (prepared.initialFailure().isPresent()) {
            return notifyFailure(prepared.initialFailure().get());
        }

        if (!options.safeLocation()) {
            return finishTeleport(context, originalTarget);
        }

        return deps.safeLocationResolver()
                .resolve(originalTarget, options.safeLocationOptions())
                .thenCompose(targetResult -> targetResult
                        .map(resolved -> returnToMainThread()
                                .thenCompose(_ -> finishTeleport(context.withTarget(resolved), resolved)))
                        .orElseGet(() -> returnToMainThread()
                                .thenCompose(_ -> notifyFailure(
                                        TeleportResults.failure(context, TeleportFailureReason.UNSAFE_LOCATION)))));
    }

    private CompletionStage<Void> returnToMainThread() {
        return deps.scheduler().supply(ExecutionTarget.global(), "teleport-main-handoff", () -> null);
    }

    private CompletionStage<TeleportResult> finishTeleport(TeleportContext context, Location resolvedTarget) {
        Player player = resolvePlayer(context.playerId());
        if (player == null) {
            return CompletableFuture.completedFuture(
                    TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }
        return deps.scheduler()
                .supply(ExecutionTarget.entity(player), "teleport-policy", () -> validatePolicies(context))
                .thenCompose(denied ->
                        denied.map(this::notifyFailure).orElseGet(() -> firePreTeleport(context, resolvedTarget)));
    }

    private Optional<TeleportResult.Failure> validatePolicies(TeleportContext context) {
        PolicyResult policyResult = deps.policyChain().validate(context);
        if (!(policyResult instanceof PolicyResult.Denied denied)) {
            return Optional.empty();
        }

        if (context.options().sendMessages()) {
            Player player = resolvePlayer(context.playerId());
            if (player != null) {
                player.sendMessage(denied.message());
            }
        }

        return Optional.of(TeleportResults.failure(context, denied.reason()));
    }

    private CompletionStage<TeleportResult> firePreTeleport(TeleportContext context, Location resolvedTarget) {
        return deps.eventNotifier()
                .firePreTeleport(context.playerId(), context.from(), resolvedTarget, context.cause(), context.source())
                .thenCompose(preEvent -> {
                    if (preEvent.isCancelled()) {
                        return notifyFailure(
                                TeleportResults.failure(context, TeleportFailureReason.CANCELLED_BY_EVENT));
                    }

                    Location eventTarget = Objects.requireNonNull(preEvent.getTo(), "preEvent.to")
                            .clone();
                    return executeTeleport(context, eventTarget);
                });
    }

    private CompletionStage<TeleportResult> executeTeleport(TeleportContext context, Location eventTarget) {
        Player player = resolvePlayer(context.playerId());
        if (player == null) {
            return CompletableFuture.completedFuture(
                    TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }
        TeleportOptions options = context.options();
        Instant startedAt = Instant.now(deps.clock());

        return deps.scheduler()
                .supply(ExecutionTarget.entity(player), "teleport-prepare", () -> {
                    preparePlayer(player, options.player());
                    return player.getVelocity();
                })
                .thenCompose(velocity -> runTeleport(player, context, eventTarget, velocity, startedAt));
    }

    private CompletionStage<TeleportResult> runTeleport(
            Player player, TeleportContext context, Location eventTarget, Vector velocity, Instant startedAt) {
        TeleportOptions options = context.options();

        CompletableFuture<Boolean> teleportFuture = startTeleport(player, eventTarget, options);

        return teleportFuture
                .orTimeout(options.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenCompose(success -> {
                    if (!success) {
                        return deps.resultMapper().mapTeleportFailure(context);
                    }

                    applyCooldown(context);

                    return deps.scheduler()
                            .supply(ExecutionTarget.entity(player), "teleport-cleanup", () -> {
                                if (options.preserveVelocity()) {
                                    player.setVelocity(velocity);
                                }
                                return null;
                            })
                            .thenCompose(_ ->
                                    deps.resultMapper().mapSuccess(context, context.from(), eventTarget, startedAt));
                })
                .exceptionallyCompose(error -> deps.resultMapper().mapException(context, error));
    }

    private CompletableFuture<Boolean> startTeleport(Player player, Location eventTarget, TeleportOptions options) {
        if (options.async()) {
            return player.teleportAsync(eventTarget);
        }

        return deps.scheduler()
                .supply(ExecutionTarget.entity(player), "sync-teleport", () -> player.teleport(eventTarget))
                .toCompletableFuture();
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
}

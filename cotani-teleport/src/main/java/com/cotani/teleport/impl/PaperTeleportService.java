package com.cotani.teleport.impl;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.PlayerSettings;
import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportOptions;
import com.cotani.teleport.api.TeleportRequest;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.api.TeleportResults;
import com.cotani.teleport.policy.PolicyResult;
import com.cotani.teleport.policy.TeleportCooldownService;
import com.cotani.teleport.policy.TeleportPolicyChain;
import com.cotani.teleport.safety.SafeLocationResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class PaperTeleportService implements com.cotani.teleport.api.TeleportService {

    private final TeleportPolicyChain policyChain;
    private final SafeLocationResolver safeLocationResolver;
    private final TeleportEventNotifier eventNotifier;
    private final TeleportResultMapper resultMapper;
    private final TeleportCooldownService cooldownService;
    private final PaperTaskScheduler scheduler;
    private final Clock clock;

    public PaperTeleportService(
            TeleportPolicyChain policyChain,
            SafeLocationResolver safeLocationResolver,
            TeleportEventNotifier eventNotifier,
            TeleportResultMapper resultMapper,
            TeleportCooldownService cooldownService,
            PaperTaskScheduler scheduler,
            Clock clock) {
        this.policyChain = policyChain;
        this.safeLocationResolver = safeLocationResolver;
        this.eventNotifier = eventNotifier;
        this.resultMapper = resultMapper;
        this.cooldownService = cooldownService;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Override
    public CompletableFuture<TeleportResult> teleport(TeleportRequest request) {
        Player player = request.player();
        Location from =
                Objects.requireNonNull(player.getLocation(), "player.location").clone();
        Location originalTarget = request.target().clone();
        TeleportOptions options = request.options();

        TeleportContext context = new TeleportContext(
                player, from, originalTarget, request.cause(), options, request.source(), Instant.now(clock));

        Optional<TeleportResult.Failure> initialFailure = TeleportValidator.validateInitial(context);
        if (initialFailure.isPresent()) {
            return notifyFailure(initialFailure.get());
        }

        if (!options.safeLocation()) {
            return finishTeleport(context, originalTarget);
        }

        return safeLocationResolver
                .resolve(originalTarget, options.safeLocationOptions())
                .thenCompose(targetResult -> targetResult
                        .map(resolved -> finishTeleport(context.withTarget(resolved), resolved))
                        .orElseGet(() -> notifyFailure(
                                TeleportResults.failure(context, TeleportFailureReason.UNSAFE_LOCATION))));
    }

    private CompletableFuture<TeleportResult> finishTeleport(TeleportContext context, Location resolvedTarget) {
        PolicyResult policyResult = policyChain.validate(context);
        if (policyResult instanceof PolicyResult.Denied denied) {
            if (context.options().sendMessages()) {
                context.player().sendMessage(denied.message());
            }
            return notifyFailure(TeleportResults.failure(context, denied.reason()));
        }

        return eventNotifier
                .firePreTeleport(context.player(), context.from(), resolvedTarget, context.cause(), context.source())
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

    private CompletableFuture<TeleportResult> executeTeleport(TeleportContext context, Location eventTarget) {
        Player player = context.player();
        TeleportOptions options = context.options();
        Instant startedAt = Instant.now(clock);

        return scheduler
                .supply(ExecutionTarget.entity(player), "teleport-prepare", () -> {
                    preparePlayer(player, options.player());

                    return player.getVelocity();
                })
                .thenCompose((Vector velocity) -> runTeleport(player, context, eventTarget, velocity, startedAt));
    }

    private CompletableFuture<TeleportResult> runTeleport(
            Player player, TeleportContext context, Location eventTarget, Vector velocity, Instant startedAt) {
        TeleportOptions options = context.options();

        CompletableFuture<Boolean> teleportFuture = startTeleport(player, eventTarget, options);

        return teleportFuture
                .orTimeout(options.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .thenCompose(success -> {
                    if (!success) {
                        return resultMapper.mapTeleportFailure(context);
                    }

                    applyCooldown(context);

                    return scheduler.supply(ExecutionTarget.entity(player), "teleport-cleanup", () -> {
                        if (options.preserveVelocity()) {
                            player.setVelocity(velocity);
                        }

                        return resultMapper
                                .mapSuccess(context, context.from(), eventTarget, startedAt)
                                .join();
                    });
                })
                .exceptionally(
                        error -> resultMapper.mapException(context, error).join());
    }

    private CompletableFuture<Boolean> startTeleport(Player player, Location eventTarget, TeleportOptions options) {
        if (options.async()) {
            return player.teleportAsync(eventTarget);
        }

        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(player.teleport(eventTarget));
        }

        return scheduler.supply(ExecutionTarget.global(), "sync-teleport", () -> player.teleport(eventTarget));
    }

    private CompletableFuture<TeleportResult> notifyFailure(TeleportResult.Failure failure) {
        return eventNotifier.fireFailure(failure).thenApply(_ -> failure);
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
            cooldownService.put(context.player().getUniqueId(), context.cause(), options.cooldownDuration());
        }
    }
}

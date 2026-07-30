package com.cotani.teleport.impl;

import com.cotani.api.InternalApi;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.*;
import com.cotani.teleport.api.TeleportService;
import com.cotani.teleport.event.CotaniPreTeleportEvent;
import com.cotani.teleport.policy.PolicyResult;
import com.cotani.teleport.policy.TeleportCooldownService;
import com.cotani.teleport.policy.TeleportPolicyChain;
import com.cotani.teleport.safety.SafeLocationResolver;
import com.cotani.text.AudienceMessages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
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
@InternalApi
public final class PaperTeleportService implements TeleportService {

    private static final Logger LOGGER = Logger.getLogger(PaperTeleportService.class.getName());

    private final Dependencies deps;
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> playerPipelines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, IndeterminateTeleport> indeterminateTeleports = new ConcurrentHashMap<>();

    public PaperTeleportService(Dependencies deps) {
        this.deps = Objects.requireNonNull(deps, "deps");
    }

    @Override
    public CompletionStage<TeleportResult> teleport(TeleportRequest request) {
        Objects.requireNonNull(request, "request");

        var result = new CompletableFuture<TeleportResult>();
        var nextPipeline = new AtomicReference<CompletableFuture<Void>>();
        var startGate = new AtomicReference<CompletableFuture<Void>>();
        playerPipelines.compute(request.playerId(), (_, previousPipeline) -> {
            var predecessor =
                    previousPipeline == null ? CompletableFuture.<Void>completedFuture(null) : previousPipeline;
            var gate = new CompletableFuture<Void>();
            var next = predecessor
                    .handle((_, _) -> null)
                    .thenCompose(_ -> gate)
                    .thenCompose(_ -> teleportOnce(request))
                    .<Void>handle((teleportResult, error) -> {
                        if (error == null) {
                            result.complete(teleportResult);
                        } else {
                            result.completeExceptionally(error);
                        }
                        return null;
                    });
            startGate.set(gate);
            nextPipeline.set(next);
            return next;
        });

        var pipeline = Objects.requireNonNull(nextPipeline.get(), "nextPipeline");
        var _ = pipeline.whenComplete((_, _) -> playerPipelines.remove(request.playerId(), pipeline));
        Objects.requireNonNull(startGate.get(), "startGate").complete(null);
        return result;
    }

    @Override
    public boolean hasIndeterminateTeleport(UUID playerId) {
        return indeterminateTeleports.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public boolean releaseIndeterminateTeleport(UUID playerId) {
        return indeterminateTeleports.remove(Objects.requireNonNull(playerId, "playerId")) != null;
    }

    private CompletionStage<TeleportResult> teleportOnce(TeleportRequest request) {
        return deps.scheduler()
                .supply(ExecutionTarget.entity(request.playerId()), "teleport-prepare", () -> prepare(request))
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
        if (indeterminateTeleports.containsKey(request.playerId())) {
            return new PreparedTeleport(
                    context,
                    originalTarget,
                    Optional.of(TeleportResults.failure(context, TeleportFailureReason.OUTCOME_INDETERMINATE)));
        }
        return new PreparedTeleport(context, originalTarget, TeleportValidator.validateInitial(context, player));
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
                .thenCompose(result -> result.map(this::notifyFailure)
                        .orElseGet(() -> firePreTeleportAndExecute(context, resolvedTarget)));
    }

    private Optional<TeleportResult.Failure> validateOrStart(TeleportContext context) {
        Player player = resolvePlayer(context.playerId());
        if (player == null) {
            return Optional.of(TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }
        var initialFailure = TeleportValidator.validateInitial(context, player);
        if (initialFailure.isPresent()) {
            return initialFailure;
        }

        PolicyResult policyResult = deps.policyChain().validate(context);
        if (!(policyResult instanceof PolicyResult.Denied denied)) {
            return Optional.empty();
        }

        if (context.options().sendMessages()) {
            AudienceMessages.sendMessage(player, denied.message());
        }

        return Optional.of(TeleportResults.failure(context, denied.reason()));
    }

    private CompletionStage<TeleportResult> firePreTeleportAndExecute(
            TeleportContext context, Location resolvedTarget) {
        Player player = resolvePlayer(context.playerId());
        if (player == null) {
            return notifyFailure(TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }

        CotaniPreTeleportEvent event = deps.eventNotifier()
                .firePreTeleportSync(player, context.from(), resolvedTarget, context.cause(), context.source());

        if (event.isCancelled()) {
            return notifyFailure(TeleportResults.failure(context, TeleportFailureReason.CANCELLED_BY_EVENT));
        }

        Location eventTarget =
                Objects.requireNonNull(event.getTo(), "preEvent.to").clone();
        if (!eventTarget.equals(resolvedTarget)) {
            return validateEventTargetAndExecute(context.withTarget(eventTarget), eventTarget);
        }
        return executeTeleport(context, eventTarget);
    }

    private CompletionStage<TeleportResult> validateEventTargetAndExecute(
            TeleportContext eventContext, Location eventTarget) {
        return deps.scheduler()
                .supply(ExecutionTarget.entity(eventContext.playerId()), "teleport-event-target-validate", () -> {
                    Player player = resolvePlayer(eventContext.playerId());
                    return TeleportValidator.validateInitial(eventContext, player);
                })
                .thenCompose(initialFailure -> initialFailure
                        .map(this::notifyFailure)
                        .orElseGet(() -> {
                            if (!eventContext.options().safeLocation()) {
                                return revalidatePoliciesAndExecute(eventContext, eventTarget);
                            }

                            return deps.safeLocationResolver()
                                    .resolve(eventTarget, eventContext.options().safeLocationOptions())
                                    .thenCompose(targetResult -> targetResult
                                            .map(resolved -> revalidatePoliciesAndExecute(
                                                    eventContext.withTarget(resolved), resolved))
                                            .orElseGet(() -> notifyFailure(TeleportResults.failure(
                                                    eventContext, TeleportFailureReason.UNSAFE_LOCATION))));
                        }));
    }

    private CompletionStage<TeleportResult> revalidatePoliciesAndExecute(
            TeleportContext context, Location finalTarget) {
        return deps.scheduler()
                .supply(
                        ExecutionTarget.entity(context.playerId()),
                        "teleport-event-target-policies",
                        () -> validateOrStart(context))
                .thenCompose(result ->
                        result.map(this::notifyFailure).orElseGet(() -> executeTeleport(context, finalTarget)));
    }

    private CompletionStage<TeleportResult> executeTeleport(TeleportContext context, Location eventTarget) {
        TeleportOptions options = context.options();
        Instant startedAt = Instant.now(deps.clock());

        return flatten(deps.scheduler().supply(ExecutionTarget.entity(context.playerId()), "teleport-execute", () -> {
            Player player = resolvePlayer(context.playerId());
            if (player == null) {
                return notifyFailure(TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
            }

            preparePlayer(player, options.player());
            Vector velocity = player.getVelocity().clone();

            if (options.async()) {
                var physicalTeleport = player.teleportAsync(eventTarget);
                return observePhysicalTeleport(
                                physicalTeleport,
                                options.timeout(),
                                options.execution().reconciliationTimeout())
                        .thenCompose(outcome -> handlePhysicalOutcome(
                                context, eventTarget, velocity, startedAt, physicalTeleport, outcome))
                        .exceptionallyCompose(error -> flatten(deps.scheduler()
                                .supply(
                                        ExecutionTarget.entity(context.playerId()),
                                        "teleport-exception",
                                        () -> deps.resultMapper().mapException(context, error))));
            }

            try {
                boolean success = player.teleport(eventTarget);
                return completeTeleport(context, eventTarget, velocity, success, startedAt);
            } catch (Throwable error) {
                return deps.resultMapper().mapException(context, error);
            }
        }));
    }

    /**
     * Observes the configured deadline without completing the public operation before Paper's
     * teleport future reaches a terminal state. Paper does not guarantee that timing out or
     * cancelling the future cancels the underlying teleport, so returning an early failure could be
     * followed by a late world mutation and a second operation for the same player.
     */
    private CompletionStage<PhysicalTeleportOutcome> observePhysicalTeleport(
            CompletableFuture<Boolean> teleportFuture, Duration timeout, Duration reconciliationTimeout) {
        return withTimeout(teleportFuture, timeout)
                .thenApply(success -> (PhysicalTeleportOutcome) new PhysicalTeleportOutcome.Confirmed(success))
                .exceptionallyCompose(error -> {
                    if (!isTimeout(error)) {
                        return CompletableFuture.failedFuture(error);
                    }
                    return withTimeout(teleportFuture, reconciliationTimeout)
                            .thenApply(
                                    success -> (PhysicalTeleportOutcome) new PhysicalTeleportOutcome.Confirmed(success))
                            .exceptionallyCompose(reconciliationError -> isTimeout(reconciliationError)
                                    ? CompletableFuture.<PhysicalTeleportOutcome>completedFuture(
                                            new PhysicalTeleportOutcome.Indeterminate())
                                    : CompletableFuture.<PhysicalTeleportOutcome>failedFuture(reconciliationError));
                });
    }

    private CompletionStage<TeleportResult> handlePhysicalOutcome(
            TeleportContext context,
            Location eventTarget,
            Vector velocity,
            Instant startedAt,
            CompletableFuture<Boolean> physicalTeleport,
            PhysicalTeleportOutcome outcome) {
        if (outcome instanceof PhysicalTeleportOutcome.Confirmed confirmed) {
            return flatten(deps.scheduler()
                    .supply(
                            ExecutionTarget.entity(context.playerId()),
                            "teleport-complete",
                            () -> completeTeleport(context, eventTarget, velocity, confirmed.success(), startedAt)));
        }
        registerLateReconciliation(context, eventTarget, velocity, startedAt, physicalTeleport);
        return notifyFailure(TeleportResults.failure(context, TeleportFailureReason.OUTCOME_INDETERMINATE));
    }

    private void registerLateReconciliation(
            TeleportContext context,
            Location eventTarget,
            Vector velocity,
            Instant startedAt,
            CompletableFuture<Boolean> physicalTeleport) {
        var pending = new IndeterminateTeleport(UUID.randomUUID(), physicalTeleport);
        indeterminateTeleports.put(context.playerId(), pending);
        var _ = physicalTeleport.whenComplete((success, error) -> {
            if (!indeterminateTeleports.remove(context.playerId(), pending)
                    || error != null
                    || !Boolean.TRUE.equals(success)) {
                return;
            }
            flatten(deps.scheduler()
                            .supply(
                                    ExecutionTarget.entity(context.playerId()),
                                    "teleport-late-reconcile",
                                    () -> completeTeleport(context, eventTarget, velocity, true, startedAt)))
                    .whenComplete((_, reconciliationError) -> {
                        if (reconciliationError != null) {
                            LOGGER.log(
                                    Level.SEVERE,
                                    "Could not reconcile late teleport for " + context.playerId(),
                                    reconciliationError);
                        }
                    });
        });
    }

    private static CompletableFuture<Boolean> withTimeout(CompletableFuture<Boolean> teleportFuture, Duration timeout) {
        long timeoutMillis;
        try {
            timeoutMillis = Math.max(1L, timeout.toMillis());
        } catch (ArithmeticException overflow) {
            timeoutMillis = Long.MAX_VALUE;
        }
        return teleportFuture.copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private static boolean isTimeout(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException || cause instanceof ExecutionException) {
            if (cause.getCause() == null) {
                break;
            }
            cause = cause.getCause();
        }
        return cause instanceof TimeoutException;
    }

    private static <T> CompletionStage<T> flatten(CompletionStage<? extends CompletionStage<T>> nested) {
        return nested.thenCompose(stage -> stage);
    }

    private CompletionStage<TeleportResult> completeTeleport(
            TeleportContext context, Location eventTarget, Vector velocity, boolean success, Instant startedAt) {
        if (!success) {
            return deps.resultMapper().mapTeleportFailure(context);
        }

        Player player = resolvePlayer(context.playerId());
        if (player == null) {
            return notifyFailure(TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }

        if (context.options().preserveVelocity()) {
            player.setVelocity(velocity);
        }

        if (context.options().checkCooldown()) {
            deps.cooldownService()
                    .put(context.playerId(), context.cause(), context.options().cooldownDuration());
        }

        return deps.resultMapper().mapSuccess(context, context.from(), eventTarget, startedAt);
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

    private @Nullable Player resolvePlayer(UUID playerId) {
        return deps.playerResolver().resolve(playerId);
    }

    record Dependencies(
            TeleportPolicyChain policyChain,
            SafeLocationResolver safeLocationResolver,
            TeleportEventNotifier eventNotifier,
            TeleportResultMapper resultMapper,
            TeleportCooldownService cooldownService,
            PaperTaskScheduler scheduler,
            Clock clock,
            PlayerResolver playerResolver) {
        Dependencies {
            Objects.requireNonNull(policyChain, "policyChain");
            Objects.requireNonNull(safeLocationResolver, "safeLocationResolver");
            Objects.requireNonNull(eventNotifier, "eventNotifier");
            Objects.requireNonNull(resultMapper, "resultMapper");
            Objects.requireNonNull(cooldownService, "cooldownService");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(playerResolver, "playerResolver");
        }

        static Dependencies create(
                TeleportPolicyChain policyChain,
                SafeLocationResolver safeLocationResolver,
                TeleportEventNotifier eventNotifier,
                TeleportResultMapper resultMapper,
                TeleportCooldownService cooldownService,
                PaperTaskScheduler scheduler,
                Clock clock) {
            return new Dependencies(
                    policyChain,
                    safeLocationResolver,
                    eventNotifier,
                    resultMapper,
                    cooldownService,
                    scheduler,
                    clock,
                    PlayerResolver.bukkit());
        }
    }

    private record PreparedTeleport(
            TeleportContext context, Location originalTarget, Optional<TeleportResult.Failure> initialFailure) {}

    private sealed interface PhysicalTeleportOutcome {

        record Confirmed(boolean success) implements PhysicalTeleportOutcome {}

        record Indeterminate() implements PhysicalTeleportOutcome {}
    }

    private record IndeterminateTeleport(UUID token, CompletableFuture<Boolean> physicalTeleport) {}
}

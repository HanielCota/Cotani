package com.cotani.teleport.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.*;
import com.cotani.teleport.event.CotaniPreTeleportEvent;
import com.cotani.teleport.policy.PolicyResult;
import com.cotani.teleport.policy.TeleportCooldownService;
import com.cotani.teleport.policy.TeleportPolicyChain;
import com.cotani.teleport.safety.SafeLocationResolver;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PaperTeleportServiceTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final Component DENY_MESSAGE = Component.text("denied");

    private final PaperTaskScheduler scheduler = mockScheduler();
    private final TeleportPolicyChain policyChain = Mockito.mock(TeleportPolicyChain.class);
    private final SafeLocationResolver safeLocationResolver = Mockito.mock(SafeLocationResolver.class);
    private final TeleportEventNotifier eventNotifier = Mockito.mock(TeleportEventNotifier.class);
    private final TeleportResultMapper resultMapper = new TeleportResultMapper(eventNotifier);
    private final TeleportCooldownService cooldownService = Mockito.mock(TeleportCooldownService.class);
    private final Clock clock = Clock.systemUTC();
    private final PlayerResolver playerResolver = Mockito.mock(PlayerResolver.class);

    private PaperTeleportService newService() {
        when(eventNotifier.fireFailure(any(TeleportResult.Failure.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(eventNotifier.firePostTeleport(
                        any(UUID.class), any(Location.class), any(Location.class), any(TeleportResult.Success.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        return new PaperTeleportService(new PaperTeleportService.Dependencies(
                policyChain,
                safeLocationResolver,
                eventNotifier,
                resultMapper,
                cooldownService,
                scheduler,
                clock,
                playerResolver));
    }

    @SuppressWarnings("unchecked")
    private static PaperTaskScheduler mockScheduler() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        doAnswer(invocation -> CompletableFuture.completedFuture(
                        ((Supplier<Object>) invocation.getArguments()[2]).get()))
                .when(scheduler)
                .supply(any(ExecutionTarget.class), anyString(), any(Supplier.class));
        return scheduler;
    }

    private static Player mockPlayer(UUID playerId, Location location) {
        Player player = Mockito.mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(location);
        when(player.getVelocity()).thenReturn(new Vector(1, 2, 3));
        return player;
    }

    private static World mockWorld() {
        World world = Mockito.mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        return world;
    }

    private static Location location(World world, double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    private static TeleportRequest request(Player player, Location target, TeleportOptions options) {
        return TeleportRequest.builder()
                .playerId(player.getUniqueId())
                .target(target)
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .source("test")
                .options(options)
                .build();
    }

    private static TeleportOptions adminSyncOptions() {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(false)
                        .checkPermission(false)
                        .checkRegion(false)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .async(false)
                .build();
    }

    private static TeleportOptions adminAsyncOptions() {
        return adminAsyncOptions(Duration.ofSeconds(10));
    }

    private static TeleportOptions adminAsyncOptions(Duration timeout) {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(false)
                        .checkPermission(false)
                        .checkRegion(false)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .execution(ExecutionSettings.builder().async(true).build())
                .timeout(timeout)
                .build();
    }

    private static TeleportOptions indeterminateAsyncOptions() {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(false)
                        .checkPermission(false)
                        .checkRegion(false)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .execution(ExecutionSettings.builder()
                        .async(true)
                        .reconciliationTimeout(Duration.ofMillis(10))
                        .build())
                .timeout(Duration.ofMillis(10))
                .build();
    }

    private static TeleportOptions safeOptions() {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(true).build())
                .policies(PolicySettings.builder().checkCooldown(false).build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .async(false)
                .build();
    }

    private static TeleportOptions adminOptionsWithMessages() {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(false)
                        .checkPermission(true)
                        .checkRegion(false)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(true).build())
                .async(false)
                .build();
    }

    private static TeleportOptions adminOptionsPreserveVelocity() {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(false)
                        .checkPermission(false)
                        .checkRegion(false)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .player(PlayerSettings.builder().preserveVelocity(true).build())
                .async(false)
                .build();
    }

    private static TeleportOptions adminOptionsWithCooldown(Duration duration) {
        return TeleportOptions.builder()
                .safety(SafetySettings.builder().safeLocation(false).build())
                .policies(PolicySettings.builder()
                        .checkCombat(false)
                        .checkCooldown(true)
                        .checkPermission(false)
                        .checkRegion(false)
                        .cooldownDuration(duration)
                        .build())
                .feedback(FeedbackSettings.builder().sendMessages(false).build())
                .async(false)
                .build();
    }

    @Test
    void syncTeleportSucceeds() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(true);

        var result = service.teleport(request(player, target, adminSyncOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(
                result instanceof TeleportResult.Success,
                "expected success but got: "
                        + (result instanceof TeleportResult.Failure failure
                                ? failure.reason() + " cause=" + failure.cause()
                                : result));
        verify(player).teleport(any(Location.class));
    }

    @Test
    void asyncTeleportSucceeds() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        var result = service.teleport(request(player, target, adminAsyncOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Success);
        verify(player).teleportAsync(any(Location.class));
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void asyncTimeoutWaitsForUnderlyingTeleportOutcome() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        var underlyingTeleport = new CompletableFuture<Boolean>();
        when(player.teleportAsync(any(Location.class))).thenReturn(underlyingTeleport);

        var result = service.teleport(request(player, target, adminAsyncOptions(Duration.ofMillis(10))))
                .toCompletableFuture();
        var deadlineObserved = new CompletableFuture<Void>();
        var timer = Executors.newSingleThreadScheduledExecutor();
        try {
            var _ = timer.schedule(() -> deadlineObserved.complete(null), 50, TimeUnit.MILLISECONDS);
            deadlineObserved.join();
            assertFalse(result.isDone(), "an unconfirmed timeout must not release the player pipeline");

            underlyingTeleport.complete(true);

            assertInstanceOf(TeleportResult.Success.class, result.join());
            verify(eventNotifier, never()).fireFailure(any(TeleportResult.Failure.class));
        } finally {
            timer.shutdownNow();
        }
    }

    @Test
    void neverCompletingPaperFutureReturnsIndeterminateAndQuarantinesPlayer() throws Exception {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        var underlyingTeleport = new CompletableFuture<Boolean>();
        when(player.teleportAsync(any(Location.class))).thenReturn(underlyingTeleport);

        var result = service.teleport(request(player, target, indeterminateAsyncOptions()))
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.OUTCOME_INDETERMINATE);
        assertTrue(service.hasIndeterminateTeleport(PLAYER_ID));

        var blocked = service.teleport(request(player, target, adminSyncOptions()))
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);
        assertTrue(blocked instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.OUTCOME_INDETERMINATE);
        verify(player, never()).teleport(any(Location.class));

        underlyingTeleport.complete(true);
        assertFalse(service.hasIndeterminateTeleport(PLAYER_ID));
        verify(eventNotifier)
                .firePostTeleport(eq(PLAYER_ID), any(Location.class), eq(target), any(TeleportResult.Success.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void syncTeleportRunsPrepareAndFinishOnEntityThread() {
        reset(scheduler);
        doAnswer(invocation -> CompletableFuture.completedFuture(
                        ((Supplier<Object>) invocation.getArguments()[2]).get()))
                .when(scheduler)
                .supply(any(ExecutionTarget.class), anyString(), any(Supplier.class));

        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(true);

        service.teleport(request(player, target, adminSyncOptions()))
                .toCompletableFuture()
                .join();

        var captor = ArgumentCaptor.forClass(ExecutionTarget.class);
        verify(scheduler, times(3)).supply(captor.capture(), anyString(), any(Supplier.class));
        var targets = captor.getAllValues();
        assertTrue(targets.stream()
                .allMatch(t -> t instanceof ExecutionTarget.EntityTarget entity
                        && entity.entityId().equals(PLAYER_ID)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void asyncTeleportCompletionRunsOnEntityThread() {
        reset(scheduler);
        doAnswer(invocation -> CompletableFuture.completedFuture(
                        ((Supplier<Object>) invocation.getArguments()[2]).get()))
                .when(scheduler)
                .supply(any(ExecutionTarget.class), anyString(), any(Supplier.class));

        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        service.teleport(request(player, target, adminAsyncOptions()))
                .toCompletableFuture()
                .join();

        var captor = ArgumentCaptor.forClass(ExecutionTarget.class);
        verify(scheduler, atLeast(3)).supply(captor.capture(), anyString(), any(Supplier.class));
        var targets = captor.getAllValues();
        assertTrue(targets.stream()
                .allMatch(t -> t instanceof ExecutionTarget.EntityTarget entity
                        && entity.entityId().equals(PLAYER_ID)));
    }

    @Test
    void offlinePlayerReturnsFailure() {
        var service = newService();
        var world = mockWorld();
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(null);

        var result = service.teleport(request(PLAYER_ID, target, adminSyncOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.PLAYER_OFFLINE);
    }

    @Test
    void targetWorldUnloadedReturnsFailure() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        var target = new Location(null, 10, 64, 10);

        var result = service.teleport(request(player, target, adminSyncOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.WORLD_UNLOADED);
    }

    @Test
    void policyDeniedReturnsFailureAndSendsMessage() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class)))
                .thenReturn(PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_PERMISSION, DENY_MESSAGE));

        var result = service.teleport(request(player, target, adminOptionsWithMessages()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.BLOCKED_BY_PERMISSION);
        verify(player).sendMessage(DENY_MESSAGE);
    }

    @Test
    void cancelledEventReturnsFailure() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        preEvent.setCancelled(true);
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);

        var result = service.teleport(request(player, target, adminSyncOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.CANCELLED_BY_EVENT);
    }

    @Test
    void failedTeleportReturnsFailure() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(false);

        var result = service.teleport(request(player, target, adminSyncOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.TELEPORT_FAILED);
    }

    @Test
    void safeLocationResolverIsUsed() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        var resolved = location(world, 12, 64, 12);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        when(safeLocationResolver.resolve(eq(target), any(SafeLocationOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(resolved)));
        var preEvent = new CotaniPreTeleportEvent(player, from, resolved, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(resolved), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(true);

        var result = service.teleport(request(player, target, safeOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Success);
        var captor = ArgumentCaptor.forClass(Location.class);
        verify(player).teleport(captor.capture());
        assertEquals(resolved, captor.getValue());
    }

    @Test
    void eventChangedSafeTargetIsResolvedAndPoliciesAreRevalidated() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var requested = location(world, 10, 64, 10);
        var initiallyResolved = location(world, 12, 64, 12);
        var eventTarget = location(world, 20, 64, 20);
        var finalResolved = location(world, 22, 64, 22);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        when(safeLocationResolver.resolve(eq(requested), any(SafeLocationOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(initiallyResolved)));
        when(safeLocationResolver.resolve(eq(eventTarget), any(SafeLocationOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(finalResolved)));
        var preEvent =
                new CotaniPreTeleportEvent(player, from, initiallyResolved, TeleportCause.PLUGIN_INTERNAL, "test");
        preEvent.setTo(eventTarget);
        when(eventNotifier.firePreTeleportSync(
                        eq(player),
                        any(Location.class),
                        eq(initiallyResolved),
                        eq(TeleportCause.PLUGIN_INTERNAL),
                        eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(true);

        var result = service.teleport(request(player, requested, safeOptions()))
                .toCompletableFuture()
                .join();

        assertInstanceOf(TeleportResult.Success.class, result);
        verify(safeLocationResolver).resolve(eq(requested), any(SafeLocationOptions.class));
        verify(safeLocationResolver).resolve(eq(eventTarget), any(SafeLocationOptions.class));
        verify(policyChain, times(2)).validate(any(TeleportContext.class));
        verify(player).teleport(finalResolved);
    }

    @Test
    void eventChangedInvalidTargetIsRejectedBeforeTeleport() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        preEvent.setTo(new Location(null, 20, 64, 20));
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);

        var result = service.teleport(request(player, target, adminSyncOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.WORLD_UNLOADED);
        verify(player, never()).teleport(any(Location.class));
        verify(player, never()).teleportAsync(any(Location.class));
    }

    @Test
    void unsafeLocationReturnsFailure() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(safeLocationResolver.resolve(eq(target), any(SafeLocationOptions.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        var result = service.teleport(request(player, target, safeOptions()))
                .toCompletableFuture()
                .join();

        assertTrue(result instanceof TeleportResult.Failure failure
                && failure.reason() == TeleportFailureReason.UNSAFE_LOCATION);
    }

    @Test
    void velocityIsPreservedWhenRequested() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(true);

        service.teleport(request(player, target, adminOptionsPreserveVelocity()))
                .toCompletableFuture()
                .join();

        verify(player).setVelocity(new Vector(1, 2, 3));
    }

    @Test
    void cooldownIsNotRegisteredOnFailure() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(false);

        var duration = Duration.ofSeconds(5);
        service.teleport(request(player, target, adminOptionsWithCooldown(duration)))
                .toCompletableFuture()
                .join();

        verify(cooldownService, never()).put(any(UUID.class), any(TeleportCause.class), any(Duration.class));
    }

    @Test
    void cooldownIsNotRegisteredOnCancelledEvent() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        preEvent.setCancelled(true);
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);

        var duration = Duration.ofSeconds(5);
        service.teleport(request(player, target, adminOptionsWithCooldown(duration)))
                .toCompletableFuture()
                .join();

        verify(cooldownService, never()).put(any(UUID.class), any(TeleportCause.class), any(Duration.class));
    }

    @Test
    void cooldownIsRegisteredWhenRequested() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var target = location(world, 10, 64, 10);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var preEvent = new CotaniPreTeleportEvent(player, from, target, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player), any(Location.class), eq(target), eq(TeleportCause.PLUGIN_INTERNAL), eq("test")))
                .thenReturn(preEvent);
        when(player.teleport(any(Location.class))).thenReturn(true);

        var duration = Duration.ofSeconds(5);
        service.teleport(request(player, target, adminOptionsWithCooldown(duration)))
                .toCompletableFuture()
                .join();

        verify(cooldownService).put(PLAYER_ID, TeleportCause.PLUGIN_INTERNAL, duration);
    }

    @Test
    void teleportsForSamePlayerExecuteSequentially() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var firstTarget = location(world, 10, 64, 10);
        var secondTarget = location(world, 20, 64, 20);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);
        when(policyChain.validate(any(TeleportContext.class))).thenReturn(PolicyResult.allowed());
        var firstEvent = new CotaniPreTeleportEvent(player, from, firstTarget, TeleportCause.PLUGIN_INTERNAL, "test");
        var secondEvent = new CotaniPreTeleportEvent(player, from, secondTarget, TeleportCause.PLUGIN_INTERNAL, "test");
        when(eventNotifier.firePreTeleportSync(
                        eq(player),
                        any(Location.class),
                        any(Location.class),
                        eq(TeleportCause.PLUGIN_INTERNAL),
                        eq("test")))
                .thenReturn(firstEvent, secondEvent);
        var firstTeleport = new CompletableFuture<Boolean>();
        when(player.teleportAsync(any(Location.class)))
                .thenReturn(firstTeleport)
                .thenReturn(CompletableFuture.completedFuture(true));

        var firstResult = service.teleport(request(player, firstTarget, adminAsyncOptions()));
        var secondResult = service.teleport(request(player, secondTarget, adminAsyncOptions()));

        verify(player, times(1)).teleportAsync(any(Location.class));
        assertFalse(secondResult.toCompletableFuture().isDone());

        firstTeleport.complete(true);

        assertInstanceOf(
                TeleportResult.Success.class, firstResult.toCompletableFuture().join());
        assertInstanceOf(
                TeleportResult.Success.class, secondResult.toCompletableFuture().join());
        verify(player, times(2)).teleportAsync(any(Location.class));
    }

    @Test
    void failsWhenTargetLocationHasNonFiniteYawOrPitch() {
        var service = newService();
        var world = mockWorld();
        var from = location(world, 0, 64, 0);
        var player = mockPlayer(PLAYER_ID, from);
        var invalidTarget = new Location(world, 10, 64, 10, Float.NaN, 0.0f);
        when(playerResolver.resolve(PLAYER_ID)).thenReturn(player);

        var result = service.teleport(request(player, invalidTarget, TeleportOptions.admin()))
                .toCompletableFuture()
                .join();

        assertInstanceOf(TeleportResult.Failure.class, result);
        var failure = (TeleportResult.Failure) result;
        assertEquals(TeleportFailureReason.INVALID_LOCATION, failure.reason());
    }

    private TeleportRequest request(UUID playerId, Location target, TeleportOptions options) {

        return TeleportRequest.builder()
                .playerId(playerId)
                .target(target)
                .cause(TeleportCause.PLUGIN_INTERNAL)
                .source("test")
                .options(options)
                .build();
    }
}

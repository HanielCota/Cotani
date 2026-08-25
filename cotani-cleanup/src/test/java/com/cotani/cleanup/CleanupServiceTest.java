package com.cotani.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.cotani.cleanup.api.CleanupEntitySnapshot;
import com.cotani.cleanup.api.CleanupExecutor;
import com.cotani.cleanup.api.CleanupPolicy;
import com.cotani.cleanup.api.CleanupRemovalResult;
import com.cotani.cleanup.api.CleanupScan;
import com.cotani.cleanup.api.CleanupService;
import com.cotani.cleanup.api.CleanupServiceOptions;
import com.cotani.cleanup.api.CleanupTarget;
import com.cotani.cleanup.api.event.CleanupCompletedEvent;
import com.cotani.cleanup.api.event.CleanupStartedEvent;
import com.cotani.cleanup.internal.InMemoryCleanupExecutor;
import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CleanupServiceTest {
    private final List<CotaniEvent> events = new ArrayList<>();
    private final EventBus eventBus = mock(EventBus.class);
    private @Nullable CleanupService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void previewNeverRemovesAndExecutionRevalidatesCandidates() {
        var oldItem = snapshot(CleanupTarget.DROPPED_ITEM, Duration.ofMinutes(10), false, false, false);
        var protectedItem = snapshot(CleanupTarget.DROPPED_ITEM, Duration.ofMinutes(10), true, false, false);
        var youngItem = snapshot(CleanupTarget.DROPPED_ITEM, Duration.ofMinutes(1), false, false, false);
        var executor = new InMemoryCleanupExecutor(List.of(oldItem, protectedItem, youngItem));
        service = create(executor);

        var policy = CleanupPolicy.defaults();
        var preview = service.previewAsync(policy).toCompletableFuture().join();
        assertEquals(3, preview.scannedEntities());
        assertEquals(1, preview.matchedEntities());
        assertEquals(0, preview.removedEntities());
        assertTrue(executor.contains(oldItem.entityId()));

        var report = service.executeAsync(policy).toCompletableFuture().join();
        assertEquals(1, report.removedEntities());
        assertFalse(executor.contains(oldItem.entityId()));
        assertTrue(executor.contains(protectedItem.entityId()));
        assertTrue(executor.contains(youngItem.entityId()));
        assertEquals(
                2, events.stream().filter(CleanupStartedEvent.class::isInstance).count());
        assertEquals(
                2,
                events.stream().filter(CleanupCompletedEvent.class::isInstance).count());
    }

    @Test
    void policyKeepsTargetsAndWorldsExplicit() {
        var world = UUID.randomUUID();
        var arrow = new CleanupEntitySnapshot(
                UUID.randomUUID(), world, 0, 0, CleanupTarget.ARROW, Duration.ofHours(1), false, false, false);
        var item = new CleanupEntitySnapshot(
                UUID.randomUUID(), world, 0, 0, CleanupTarget.DROPPED_ITEM, Duration.ofHours(1), false, false, false);
        var executor = new InMemoryCleanupExecutor(List.of(arrow, item));
        service = create(executor);

        var policy = CleanupPolicy.builder()
                .targets(List.of(CleanupTarget.ARROW))
                .worlds(List.of(world))
                .minimumAge(Duration.ZERO)
                .build();
        var report = service.executeAsync(policy).toCompletableFuture().join();

        assertEquals(1, report.matchedEntities());
        assertEquals(1, report.removedEntities());
        assertFalse(executor.contains(arrow.entityId()));
        assertTrue(executor.contains(item.entityId()));
    }

    @Test
    void maxEntitiesBoundsOneScan() {
        var first = snapshot(CleanupTarget.DROPPED_ITEM, Duration.ofHours(1), false, false, false);
        var second = snapshot(CleanupTarget.EXPERIENCE_ORB, Duration.ofHours(1), false, false, false);
        var serviceExecutor = new InMemoryCleanupExecutor(List.of(first, second));
        service = create(serviceExecutor);

        var report = service.previewAsync(CleanupPolicy.builder().maxEntities(1).build())
                .toCompletableFuture()
                .join();

        assertEquals(2, report.scannedEntities());
        assertEquals(2, report.matchedEntities());
        assertEquals(1, report.selectedEntities());
    }

    @Test
    void protectedTagsAndServiceClockAreApplied() {
        var protectedEntity = new CleanupEntitySnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                0,
                CleanupTarget.DROPPED_ITEM,
                Duration.ofHours(1),
                false,
                false,
                false,
                Set.of("myplugin:protected"));
        var executor = new InMemoryCleanupExecutor(List.of(protectedEntity));
        var fixedClock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
        configureEventBus();
        service = CotaniCleanups.fromExecutor(executor, eventBus, CleanupServiceOptions.defaults(), fixedClock);

        var policy = CleanupPolicy.builder()
                .protectedTags(List.of("myplugin:protected"))
                .build();
        var request = service.newRequest(policy, "clock-test");
        var report = service.executeAsync(request).toCompletableFuture().join();

        assertEquals(fixedClock.instant(), request.requestedAt());
        assertEquals(0, report.matchedEntities());
        assertTrue(executor.contains(protectedEntity.entityId()));
    }

    @Test
    void timeoutDoesNotFreeThePendingSlotBeforeTheExecutorCompletes() {
        var pendingScan = new CompletableFuture<CleanupScan>();
        CleanupExecutor hangingExecutor = new CleanupExecutor() {
            @Override
            public CompletionStage<CleanupScan> scanAsync(CleanupPolicy policy) {
                return pendingScan;
            }

            @Override
            public CompletionStage<CleanupRemovalResult> removeAsync(
                    CleanupPolicy policy, List<CleanupEntitySnapshot> candidates) {
                return CompletableFuture.completedFuture(CleanupRemovalResult.empty());
            }
        };
        configureEventBus();
        service = CotaniCleanups.fromExecutor(
                hangingExecutor, eventBus, new CleanupServiceOptions(Duration.ofMillis(10), Duration.ofSeconds(1), 1));

        var first = service.previewAsync(CleanupPolicy.defaults());
        var timeout = assertThrows(CompletionException.class, first.toCompletableFuture()::join);
        assertTrue(timeout.getCause() instanceof TimeoutException);

        var second = service.previewAsync(CleanupPolicy.defaults());
        var rejected = assertThrows(CompletionException.class, second.toCompletableFuture()::join);
        assertTrue(rejected.getCause() instanceof RejectedExecutionException);

        pendingScan.complete(new CleanupScan(0, 0, List.of(), Map.of()));
        service.closeAsync().toCompletableFuture().join();
    }

    private CleanupService create(InMemoryCleanupExecutor executor) {
        configureEventBus();
        return CotaniCleanups.fromExecutor(executor, eventBus);
    }

    private void configureEventBus() {
        doAnswer(invocation -> {
                    var event = invocation.getArgument(0, CotaniEvent.class);
                    events.add(event);
                    return CompletableFuture.completedFuture(event);
                })
                .when(eventBus)
                .publishAsync(any());
    }

    private static CleanupEntitySnapshot snapshot(
            CleanupTarget target, Duration age, boolean named, boolean persistent, boolean tamed) {
        return new CleanupEntitySnapshot(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, target, age, named, persistent, tamed);
    }
}

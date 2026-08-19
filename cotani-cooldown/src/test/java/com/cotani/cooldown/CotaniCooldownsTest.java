package com.cotani.cooldown;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.cooldown.api.DistributedCooldownService;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CotaniCooldownsTest {
    @Test
    void shouldCreateInMemoryService() {
        var service = CotaniCooldowns.inMemory();

        assertTrue(service.user(UUID.randomUUID())
                .action("use")
                .duration(Duration.ofSeconds(5))
                .checkAndStart()
                .allowed());
    }

    @Test
    void shouldCreateInMemoryServiceWithProvidedClock() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var service = CotaniCooldowns.inMemory(clock);

        assertTrue(service.global()
                .action("use")
                .duration(Duration.ofSeconds(5))
                .checkAndStart()
                .allowed());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullClock() {
        assertThrows(NullPointerException.class, () -> CotaniCooldowns.inMemory(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPlayerCache() {
        assertThrows(NullPointerException.class, () -> CotaniCooldowns.cacheBacked(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullStorageInDistributedFactory() {
        var scheduler = mock(PaperTaskScheduler.class);

        assertThrows(NullPointerException.class, () -> CotaniCooldowns.distributed(null, scheduler));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullSchedulerInDistributedFactory() {
        var storage = mock(CotaniStorage.class);

        assertThrows(NullPointerException.class, () -> CotaniCooldowns.distributed(storage, null));
    }

    @Test
    void shouldRejectNonPositiveCleanupInterval() {
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> CotaniCooldowns.distributed(storage, scheduler, Clock.systemUTC(), Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> CotaniCooldowns.distributed(storage, scheduler, Clock.systemUTC(), Duration.ofSeconds(-1)));
    }

    @Test
    void shouldUseFiveMinuteCleanupIntervalByDefault() {
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());

        CotaniCooldowns.distributed(storage, scheduler);

        verify(scheduler).asyncTimer(any(), eq(Duration.ofMinutes(5)), eq(Duration.ofMinutes(5)));
    }

    @Test
    void shouldCancelCleanupTaskOnClose() {
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);
        var cleanupTask = mock(SchedulerTask.class);
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(cleanupTask);

        DistributedCooldownService service =
                CotaniCooldowns.distributed(storage, scheduler, Clock.systemUTC(), Duration.ofMinutes(1));

        service.close();

        verify(cleanupTask).cancel();
    }
}

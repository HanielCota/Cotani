package com.cotani.teleport.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.teleport.api.TeleportCause;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeleportCooldownServiceTest {

    private final MutableClock clock = new MutableClock(Instant.ofEpochSecond(1000), ZoneId.of("UTC"));
    private TeleportCooldownService service;

    private static final class MutableClock extends Clock {
        private Instant instant;

        @SuppressWarnings("UnusedVariable")
        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @BeforeEach
    @SuppressWarnings("NullAway")
    void setUp() {
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        org.mockito.Mockito.when(scheduler.asyncTimer(
                        org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any()))
                .thenReturn(SchedulerTask.noop());
        service = new TeleportCooldownService(clock, scheduler);
    }

    @Test
    void isNotOnCooldownInitially() {
        var id = UUID.randomUUID();
        assertFalse(service.isOnCooldown(id, TeleportCause.PLUGIN_INTERNAL));
    }

    @Test
    void isOnCooldownAfterPut() {
        var id = UUID.randomUUID();
        service.put(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMinutes(5));
        assertTrue(service.isOnCooldown(id, TeleportCause.PLUGIN_INTERNAL));
    }

    @Test
    void cooldownExpiresAfterDuration() {
        var id = UUID.randomUUID();
        service.put(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMillis(1));
        assertTrue(service.isOnCooldown(id, TeleportCause.PLUGIN_INTERNAL));
        clock.advance(Duration.ofMillis(10));
        assertFalse(service.isOnCooldown(id, TeleportCause.PLUGIN_INTERNAL));
    }

    @Test
    void tryAcquireSucceedsWhenNotOnCooldown() {
        var id = UUID.randomUUID();
        assertTrue(service.tryAcquire(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMinutes(5)));
    }

    @Test
    void tryAcquireFailsWhenOnCooldown() {
        var id = UUID.randomUUID();
        service.put(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMinutes(5));
        assertFalse(service.tryAcquire(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMinutes(5)));
    }

    @Test
    void clearRemovesCooldown() {
        var id = UUID.randomUUID();
        service.put(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMinutes(5));
        service.clear(id, TeleportCause.PLUGIN_INTERNAL);
        assertFalse(service.isOnCooldown(id, TeleportCause.PLUGIN_INTERNAL));
    }

    @Test
    void clearAllRemovesAllCooldowns() {
        var id = UUID.randomUUID();
        service.put(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMinutes(5));
        service.put(id, TeleportCause.UNKNOWN, Duration.ofMinutes(5));
        service.clearAll(id);
        assertFalse(service.isOnCooldown(id, TeleportCause.PLUGIN_INTERNAL));
        assertFalse(service.isOnCooldown(id, TeleportCause.UNKNOWN));
    }

    @Test
    void cooldownIsPerCause() {
        var id = UUID.randomUUID();
        service.put(id, TeleportCause.PLUGIN_INTERNAL, Duration.ofMinutes(5));
        assertTrue(service.isOnCooldown(id, TeleportCause.PLUGIN_INTERNAL));
        assertFalse(service.isOnCooldown(id, TeleportCause.UNKNOWN));
    }
}

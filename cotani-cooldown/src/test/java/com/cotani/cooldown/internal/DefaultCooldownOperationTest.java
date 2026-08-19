package com.cotani.cooldown.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownOperation;
import com.cotani.cooldown.api.CooldownTargets;
import com.cotani.cooldown.paper.CotaniCooldownDenyEvent;
import com.cotani.cooldown.paper.CotaniCooldownStartEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class DefaultCooldownOperationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final CooldownKey KEY = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));
    private static final Duration DURATION = Duration.ofSeconds(5);

    private final InMemoryCooldownStore store = new InMemoryCooldownStore();
    private final MutableClock clock = new MutableClock(NOW);

    @Test
    void shouldThrowWhenActionIsMissingOnCheck() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global());

        assertThrows(IllegalStateException.class, operation::check);
    }

    @Test
    void shouldThrowWhenActionIsMissingOnStart() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global());

        assertThrows(IllegalStateException.class, operation::start);
    }

    @Test
    void shouldThrowWhenActionIsMissingOnRemove() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global());

        assertThrows(IllegalStateException.class, operation::remove);
    }

    @Test
    void shouldThrowWhenDurationIsMissingOnCheckAndStart() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global()).action("use");

        assertThrows(IllegalStateException.class, operation::checkAndStart);
    }

    @Test
    void shouldThrowWhenDurationIsMissingOnRestart() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global()).action("use");

        assertThrows(IllegalStateException.class, operation::restart);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullAction() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global());

        assertThrows(NullPointerException.class, () -> operation.action((String) null));
        assertThrows(NullPointerException.class, () -> operation.action((CooldownAction) null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullDuration() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global());

        assertThrows(NullPointerException.class, () -> operation.duration(null));
    }

    @Test
    void shouldRejectNonPositiveDuration() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global());

        assertThrows(IllegalArgumentException.class, () -> operation.duration(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> operation.duration(Duration.ofSeconds(-1)));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPolicy() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global());

        assertThrows(NullPointerException.class, () -> operation.policy(null));
    }

    @Test
    void shouldApplyPolicyDuration() {
        var operation = new DefaultCooldownOperation(store, clock, CooldownTargets.global())
                .action("use")
                .policy(() -> DURATION);

        assertTrue(operation.checkAndStart().allowed());
        var denied = operation.checkAndStart();
        assertTrue(denied.denied());
        assertEquals(DURATION, denied.remaining());
    }

    @Test
    void shouldAllowWhenNoCooldownIsActive() {
        var result = operation().check();

        assertTrue(result.allowed());
        assertEquals(KEY, result.key());
    }

    @Test
    void shouldDenyWhileCooldownIsActive() {
        operation().checkAndStart();

        var result = operation().check();

        assertTrue(result.denied());
        assertEquals(DURATION, result.remaining());
        assertEquals(NOW.plus(DURATION), result.expiresAtOptional().orElseThrow());
    }

    @Test
    void shouldAllowAndPurgeExpiredEntryOnCheck() {
        operation().checkAndStart();
        clock.advance(Duration.ofSeconds(6));

        var result = operation().check();

        assertTrue(result.allowed());
        assertTrue(store.find(KEY).isEmpty());
    }

    @Test
    void shouldReportRemainingDuration() {
        var operation = operation();

        assertEquals(Optional.empty(), operation.remaining());

        operation.checkAndStart();

        assertEquals(Optional.of(DURATION), operation.remaining());
    }

    @Test
    void shouldReportActiveState() {
        var operation = operation();

        assertFalse(operation.active());

        operation.checkAndStart();

        assertTrue(operation.active());
    }

    @Test
    void shouldRemoveCooldown() {
        var operation = operation();
        operation.checkAndStart();

        operation.remove();

        assertTrue(operation.check().allowed());
        assertTrue(store.find(KEY).isEmpty());
    }

    @Test
    void shouldRestartCooldownFromNow() {
        var operation = operation();
        operation.checkAndStart();
        clock.advance(Duration.ofSeconds(3));

        var result = operation.restart();

        assertTrue(result.allowed());
        var denied = operation.check();
        assertTrue(denied.denied());
        assertEquals(DURATION, denied.remaining());
    }

    @Test
    void shouldStartCooldownOnlyOncePerKey() {
        var operation = operation();

        assertTrue(operation.checkAndStart().allowed());
        assertTrue(operation.checkAndStart().denied());
        assertEquals(1L, store.estimatedSize());
    }

    @Test
    void shouldFireStartEventWhenCooldownIsStarted() {
        var pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            operation().checkAndStart();
        }

        ArgumentCaptor<CotaniCooldownStartEvent> captor = ArgumentCaptor.forClass(CotaniCooldownStartEvent.class);
        verify(pluginManager).callEvent(captor.capture());
        assertEquals(KEY, captor.getValue().getKey());
        assertEquals(DURATION, captor.getValue().getDuration());
    }

    @Test
    void shouldFireDenyEventWhenCooldownIsDenied() {
        var pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            operation().checkAndStart();
            operation().checkAndStart();
        }

        ArgumentCaptor<CotaniCooldownDenyEvent> captor = ArgumentCaptor.forClass(CotaniCooldownDenyEvent.class);
        verify(pluginManager).callEvent(captor.capture());
        assertEquals(KEY, captor.getValue().getKey());
        assertEquals(DURATION, captor.getValue().getRemaining());
        assertEquals(NOW.plus(DURATION), captor.getValue().getExpiresAt());
    }

    @Test
    void shouldNotFireEventsWhenCalledOffThePrimaryThread() {
        var pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

            operation().checkAndStart();
        }

        verify(pluginManager, never()).callEvent(any());
    }

    @Test
    void shouldNotFireEventsWhenNoServerIsAvailable() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            operation().checkAndStart();
        }

        assertTrue(store.find(KEY).isPresent());
    }

    private CooldownOperation operation() {
        return new DefaultCooldownOperation(store, clock, CooldownTargets.global())
                .action("use")
                .duration(DURATION);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
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

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

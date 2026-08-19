package com.cotani.cooldown.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownStore;
import com.cotani.cooldown.api.CooldownTarget;
import com.cotani.cooldown.api.CooldownTargets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultCooldownServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final MutableClock CLOCK = new MutableClock(NOW);

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullTarget() {
        var service = new DefaultCooldownService(mock(CooldownStore.class), CLOCK);

        assertThrows(NullPointerException.class, () -> service.target(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArgumentsOnRemove() {
        var service = new DefaultCooldownService(mock(CooldownStore.class), CLOCK);

        assertThrows(NullPointerException.class, () -> service.remove(null, CooldownAction.of("use")));
        assertThrows(NullPointerException.class, () -> service.remove(CooldownTargets.global(), null));
    }

    @Test
    void shouldRouteUserGlobalAndResourceShortcuts() {
        var store = new InMemoryCooldownStore();
        var service = new DefaultCooldownService(store, CLOCK);
        var userId = UUID.randomUUID();

        assertTrue(service.user(userId)
                .action("use")
                .duration(Duration.ofSeconds(5))
                .checkAndStart()
                .allowed());
        assertTrue(service.global()
                .action("use")
                .duration(Duration.ofSeconds(5))
                .checkAndStart()
                .allowed());
        assertTrue(service.resource("spawn")
                .action("use")
                .duration(Duration.ofSeconds(5))
                .checkAndStart()
                .allowed());

        assertTrue(store.find(new CooldownKey(CooldownTargets.user(userId), CooldownAction.of("use")))
                .isPresent());
        assertTrue(store.find(new CooldownKey(CooldownTargets.global(), CooldownAction.of("use")))
                .isPresent());
        assertTrue(store.find(new CooldownKey(CooldownTargets.resource("spawn"), CooldownAction.of("use")))
                .isPresent());
    }

    @Test
    void shouldDelegateRemoveToStore() {
        var store = mock(CooldownStore.class);
        var service = new DefaultCooldownService(store, CLOCK);
        CooldownTarget target = CooldownTargets.global();
        var action = CooldownAction.of("use");

        service.remove(target, action);

        verify(store).remove(new CooldownKey(target, action));
    }

    @Test
    void shouldDelegateClearExpiredToStore() {
        var store = mock(CooldownStore.class);
        var service = new DefaultCooldownService(store, CLOCK);

        service.clearExpired();

        verify(store).removeExpired(CLOCK);
    }

    @Test
    void shouldDelegateClearAllToStore() {
        var store = mock(CooldownStore.class);
        var service = new DefaultCooldownService(store, CLOCK);

        service.clearAll();

        verify(store).clear();
    }

    private static final class MutableClock extends Clock {
        private final Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
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

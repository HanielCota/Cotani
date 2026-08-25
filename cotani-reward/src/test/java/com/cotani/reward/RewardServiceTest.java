package com.cotani.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.RewardClaimConflictException;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardDefinition;
import com.cotani.reward.api.RewardGrantHandler;
import com.cotani.reward.api.RewardNotFoundException;
import com.cotani.reward.api.RewardOnCooldownException;
import com.cotani.reward.api.RewardService;
import com.cotani.reward.api.RewardServiceOptions;
import com.cotani.reward.internal.InMemoryRewardRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class RewardServiceTest {
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_PLAYER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void claimsIdempotentlyAndTracksStreaks() {
        var clock = new MutableClock(START);
        var service = service(clock);
        var definition = definition(Duration.ofHours(24), Duration.ofDays(2), 3);
        service.register(definition);
        var claimId = new RewardClaimId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        var first = service.claimAsync(PLAYER, definition.id(), claimId)
                .toCompletableFuture()
                .join();
        assertEquals(1, first.streak());
        assertEquals(1, first.totalClaims());
        assertEquals(
                1, service.pendingClaimsAsync(10).toCompletableFuture().join().size());
        assertTrue(service.markSettledAsync(claimId).toCompletableFuture().join());
        assertTrue(service.pendingClaimsAsync(10).toCompletableFuture().join().isEmpty());
        assertTrue(service.markSettledAsync(claimId).toCompletableFuture().join());
        assertEquals(
                first,
                service.claimAsync(PLAYER, definition.id(), claimId)
                        .toCompletableFuture()
                        .join());

        var cooldownFailure = assertThrows(
                CompletionException.class,
                () -> service.claimAsync(PLAYER, definition.id(), RewardClaimId.random())
                        .toCompletableFuture()
                        .join());
        assertTrue(cooldownFailure.getCause() instanceof RewardOnCooldownException);

        clock.advance(Duration.ofHours(24));
        var second = service.claimAsync(PLAYER, definition.id())
                .toCompletableFuture()
                .join();
        assertEquals(2, second.streak());

        clock.advance(Duration.ofDays(3));
        var reset = service.claimAsync(PLAYER, definition.id())
                .toCompletableFuture()
                .join();
        assertEquals(1, reset.streak());
    }

    @Test
    void rejectsUnknownRewardsAndConflictingClaimIds() {
        var service = service(Clock.fixed(START, ZoneId.of("UTC")));
        var definition = definition(Duration.ofHours(1), Duration.ofDays(1), 5);
        service.register(definition);
        var claimId = RewardClaimId.random();
        service.claimAsync(PLAYER, definition.id(), claimId)
                .toCompletableFuture()
                .join();

        var conflict = assertThrows(
                CompletionException.class,
                () -> service.claimAsync(OTHER_PLAYER, definition.id(), claimId)
                        .toCompletableFuture()
                        .join());
        assertTrue(conflict.getCause() instanceof RewardClaimConflictException);

        var unknown = assertThrows(
                CompletionException.class,
                () -> service.claimAsync(PLAYER, com.cotani.reward.api.RewardId.of("unknown"))
                        .toCompletableFuture()
                        .join());
        assertTrue(unknown.getCause() instanceof RewardNotFoundException);
    }

    @Test
    void rejectsRegistrationAfterClose() {
        var service = service(Clock.fixed(START, ZoneId.of("UTC")));
        service.closeAsync().toCompletableFuture().join();

        assertThrows(
                IllegalStateException.class,
                () -> service.register(definition(Duration.ofHours(1), Duration.ofHours(1), 1)));
    }

    @Test
    void settlesAllGrantsBeforeAcknowledgingAndRecoversPendingClaim() {
        var service = service(Clock.fixed(START, ZoneId.of("UTC")));
        var definition = definition(Duration.ofHours(24), Duration.ofDays(2), 3);
        service.register(definition);
        var delivered = new java.util.concurrent.atomic.AtomicInteger();
        var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        var handler = new RewardGrantHandler() {
            @Override
            public boolean supports(com.cotani.reward.api.RewardGrant grant) {
                return true;
            }

            @Override
            public java.util.concurrent.CompletionStage<Void> settleAsync(
                    RewardSettlementContext context, com.cotani.reward.api.RewardGrant grant) {
                delivered.incrementAndGet();
                if (failOnce.getAndSet(false)) {
                    return java.util.concurrent.CompletableFuture.failedFuture(
                            new IllegalStateException("delivery unavailable"));
                }
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
        };
        var settlement = CotaniRewards.settlement(service, List.of(handler));

        assertThrows(
                CompletionException.class,
                () -> settlement
                        .claimOrRecoverAsync(PLAYER, definition.id())
                        .toCompletableFuture()
                        .join());
        assertEquals(
                1, service.pendingClaimsAsync(10).toCompletableFuture().join().size());

        var settled = settlement
                .claimOrRecoverAsync(PLAYER, definition.id())
                .toCompletableFuture()
                .join();

        assertEquals(2, delivered.get());
        assertTrue(service.pendingClaimsAsync(10).toCompletableFuture().join().isEmpty());
        assertEquals(settled.playerId(), PLAYER);
    }

    private static RewardService service(Clock clock) {
        return CotaniRewards.fromRepository(
                new InMemoryRewardRepository(),
                new RewardServiceOptions(Duration.ofSeconds(1), Duration.ofDays(90)),
                clock);
    }

    private static RewardDefinition definition(Duration cooldown, Duration streakWindow, int maxStreak) {
        return new RewardDefinition(
                com.cotani.reward.api.RewardId.of("daily"),
                cooldown,
                streakWindow,
                maxStreak,
                List.of(new CurrencyGrant("coins", new BigDecimal("100"))));
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
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
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}

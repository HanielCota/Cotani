package com.cotani.season.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.event.api.EventBus;
import com.cotani.reward.CotaniRewards;
import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.RewardDefinition;
import com.cotani.reward.api.RewardId;
import com.cotani.reward.api.RewardService;
import com.cotani.season.api.SeasonDefinition;
import com.cotani.season.api.SeasonExperienceId;
import com.cotani.season.api.SeasonId;
import com.cotani.season.api.SeasonLevel;
import com.cotani.season.api.SeasonLevelLockedException;
import com.cotani.season.api.SeasonServiceOptions;
import com.cotani.testkit.StressTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class DefaultSeasonServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");
    private static final SeasonId SEASON_ID = SeasonId.of("summer-2026");

    @Test
    @Tag("stress")
    void generatedPlayersGainExperienceAndClaimLevelsIdempotently() {
        var fixture = new Fixture();
        var service = fixture.service();
        try {
            StressTestSupport.scenarios("season", "experience-level-claim", (context, random, player) -> {
                var operationId = new SeasonExperienceId(random.uuid("season-experience"));
                var first = StressTestSupport.await(
                        service.addExperienceAsync(player.id(), SEASON_ID, 1_000, operationId),
                        Duration.ofSeconds(30),
                        context);
                var replay = StressTestSupport.await(
                        service.addExperienceAsync(player.id(), SEASON_ID, 1_000, operationId),
                        Duration.ofSeconds(30),
                        context);
                assertEquals(first, replay, context::description);

                var claim = StressTestSupport.await(
                        service.claimLevelAsync(player.id(), SEASON_ID, 2), Duration.ofSeconds(30), context);
                var repeated = StressTestSupport.await(
                        service.claimLevelAsync(player.id(), SEASON_ID, 2), Duration.ofSeconds(30), context);
                assertEquals(claim.claimId(), repeated.claimId(), context::description);
                assertEquals(1_000, replay.experience(), context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void appliesExperienceExactlyOnceForTheSameOperation() {
        var fixture = new Fixture();
        var playerId = UUID.randomUUID();
        var operationId = SeasonExperienceId.random();

        var first = fixture.service()
                .addExperienceAsync(playerId, SEASON_ID, 250, operationId)
                .toCompletableFuture()
                .join();
        var repeated = fixture.service()
                .addExperienceAsync(playerId, SEASON_ID, 250, operationId)
                .toCompletableFuture()
                .join();

        assertEquals(250, first.experience());
        assertEquals(first, repeated);
    }

    @Test
    void claimsUnlockedLevelWithAStableRewardClaim() {
        var fixture = new Fixture();
        var playerId = UUID.randomUUID();
        var service = fixture.service();

        var locked = assertThrows(
                CompletionException.class,
                () -> service.claimLevelAsync(playerId, SEASON_ID, 2)
                        .toCompletableFuture()
                        .join());
        assertTrue(locked.getCause() instanceof SeasonLevelLockedException);

        service.addExperienceAsync(playerId, SEASON_ID, 1_000)
                .toCompletableFuture()
                .join();
        var firstClaim = service.claimLevelAsync(playerId, SEASON_ID, 2)
                .toCompletableFuture()
                .join();
        var repeatedClaim = service.claimLevelAsync(playerId, SEASON_ID, 2)
                .toCompletableFuture()
                .join();
        var progress = service.findProgressAsync(playerId, SEASON_ID)
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertEquals(firstClaim.claimId(), repeatedClaim.claimId());
        assertEquals(1_000, progress.experience());
        assertEquals(java.util.Set.of(2), progress.claimedLevels());
    }

    @Test
    void rejectsExperienceOutsideTheSeasonWindow() {
        var fixture = new Fixture();
        var service = DefaultSeasonService.create(
                new InMemorySeasonRepository(),
                fixture.rewards,
                fixture.eventBus,
                SeasonServiceOptions.defaults(),
                Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));
        service.register(fixture.definition);

        assertThrows(
                RuntimeException.class,
                () -> service.addExperienceAsync(UUID.randomUUID(), SEASON_ID, 1)
                        .toCompletableFuture()
                        .join());
    }

    private static final class Fixture {
        private final EventBus eventBus = mock(EventBus.class);
        private final RewardService rewards = CotaniRewards.inMemory();
        private final SeasonDefinition definition = new SeasonDefinition(
                SEASON_ID,
                "Summer 2026",
                NOW.minusSeconds(60),
                NOW.plusSeconds(60),
                List.of(
                        new SeasonLevel(1, 0, RewardId.of("summer-level-1")),
                        new SeasonLevel(2, 1_000, RewardId.of("summer-level-2"))));
        private final InMemorySeasonRepository repository = new InMemorySeasonRepository();

        private Fixture() {
            when(eventBus.publishAsync(any()))
                    .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)));
            rewards.register(reward("summer-level-1"));
            rewards.register(reward("summer-level-2"));
        }

        private DefaultSeasonService service() {
            var service = DefaultSeasonService.create(
                    repository, rewards, eventBus, SeasonServiceOptions.defaults(), Clock.fixed(NOW, ZoneOffset.UTC));
            service.register(definition);
            return service;
        }

        private static RewardDefinition reward(String id) {
            return new RewardDefinition(
                    RewardId.of(id),
                    Duration.ofDays(1),
                    Duration.ofDays(2),
                    1,
                    List.of(new CurrencyGrant("coins", BigDecimal.ONE)));
        }
    }
}

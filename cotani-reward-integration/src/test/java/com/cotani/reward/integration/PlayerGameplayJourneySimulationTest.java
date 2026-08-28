package com.cotani.reward.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.cotani.achievement.CotaniAchievements;
import com.cotani.achievement.api.AchievementCriterion;
import com.cotani.achievement.api.AchievementDefinition;
import com.cotani.achievement.api.AchievementId;
import com.cotani.event.api.EventBus;
import com.cotani.event.subscription.EventSubscription;
import com.cotani.quest.CotaniQuests;
import com.cotani.quest.api.QuestClaimId;
import com.cotani.quest.api.QuestDefinition;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestObjective;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.ranking.CotaniRankings;
import com.cotani.ranking.api.RankingDefinition;
import com.cotani.ranking.api.RankingId;
import com.cotani.reward.CotaniRewards;
import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.RewardDefinition;
import com.cotani.reward.api.RewardId;
import com.cotani.season.CotaniSeasons;
import com.cotani.season.api.SeasonDefinition;
import com.cotani.season.api.SeasonExperienceId;
import com.cotani.season.api.SeasonId;
import com.cotani.season.api.SeasonLevel;
import com.cotani.statistics.CotaniStatistics;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticOperationId;
import com.cotani.testkit.StressTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Cross-module journey from gameplay progress through durable, idempotent reward claims. */
@Tag("stress")
@Tag("player-simulation")
class PlayerGameplayJourneySimulationTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final StatisticId SCORE = StatisticId.of("journey-score");
    private static final QuestId QUEST = QuestId.of("journey-quest");
    private static final QuestObjectiveId OBJECTIVE = QuestObjectiveId.of("journey-objective");
    private static final AchievementId ACHIEVEMENT = AchievementId.of("journey-achievement");
    private static final SeasonId SEASON = SeasonId.of("journey-season");
    private static final RankingId RANKING = RankingId.of("journey-ranking");
    private static final RewardId QUEST_REWARD = RewardId.of("journey-quest-reward");
    private static final RewardId ACHIEVEMENT_REWARD = RewardId.of("journey-achievement-reward");
    private static final RewardId SEASON_REWARD = RewardId.of("journey-season-reward");

    @Test
    void oneThousandPlayersCompleteAProgressionJourneyWithoutDuplicateState() {
        var eventBus = eventBus();
        var rewards = CotaniRewards.inMemory();
        registerReward(rewards, QUEST_REWARD);
        registerReward(rewards, ACHIEVEMENT_REWARD);
        registerReward(rewards, SEASON_REWARD);
        var statistics = CotaniStatistics.inMemory(eventBus);
        var quests = CotaniQuests.inMemory(eventBus);
        quests.register(new QuestDefinition(
                QUEST, List.of(new QuestObjective(OBJECTIVE, "score", "journey", 10)), QUEST_REWARD));
        var achievements = CotaniAchievements.inMemory(statistics, rewards, eventBus);
        achievements.register(new AchievementDefinition(
                ACHIEVEMENT, List.of(new AchievementCriterion(SCORE, 10)), Optional.of(ACHIEVEMENT_REWARD)));
        var seasons = CotaniSeasons.inMemory(rewards, eventBus);
        var now = Instant.now();
        seasons.register(new SeasonDefinition(
                SEASON,
                "Journey season",
                now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(1)),
                List.of(new SeasonLevel(1, 0, SEASON_REWARD), new SeasonLevel(2, 10, SEASON_REWARD))));
        var rankings = CotaniRankings.fromStatistics(statistics);
        rankings.register(new RankingDefinition(RANKING, SCORE, 1_000));

        try {
            StressTestSupport.scenarios("player-journey", "progression", (context, random, player) -> {
                var statisticOperation = StatisticOperationId.of(random.uuid("statistic-operation"));
                var statistic = StressTestSupport.await(
                        statistics.incrementAsync(player.id(), SCORE, 10, statisticOperation), TIMEOUT, context);
                var statisticReplay = StressTestSupport.await(
                        statistics.incrementAsync(player.id(), SCORE, 10, statisticOperation), TIMEOUT, context);
                assertEquals(statistic, statisticReplay, context::description);

                var questProgress = StressTestSupport.await(
                        quests.recordProgressAsync(player.id(), QUEST, OBJECTIVE, 10), TIMEOUT, context);
                assertTrue(questProgress.completed(), context::description);
                var questClaim = StressTestSupport.await(
                        quests.claimAsync(player.id(), QUEST, new QuestClaimId(random.uuid("quest-claim"))),
                        TIMEOUT,
                        context);
                var rewardClaim = StressTestSupport.await(
                        rewards.claimAsync(player.id(), QUEST_REWARD, questClaim.rewardClaimId()), TIMEOUT, context);
                assertTrue(
                        StressTestSupport.await(rewards.markSettledAsync(rewardClaim.claimId()), TIMEOUT, context),
                        context::description);

                var achievement =
                        StressTestSupport.await(achievements.evaluateAsync(player.id(), ACHIEVEMENT), TIMEOUT, context);
                assertTrue(achievement.unlocked(), context::description);
                var achievementClaim = StressTestSupport.await(
                        achievements.claimRewardAsync(player.id(), ACHIEVEMENT), TIMEOUT, context);
                assertTrue(
                        StressTestSupport.await(rewards.markSettledAsync(achievementClaim.claimId()), TIMEOUT, context),
                        context::description);

                var seasonProgress = StressTestSupport.await(
                        seasons.addExperienceAsync(
                                player.id(), SEASON, 10, new SeasonExperienceId(random.uuid("season-operation"))),
                        TIMEOUT,
                        context);
                assertEquals(10, seasonProgress.experience(), context::description);
                var seasonClaim =
                        StressTestSupport.await(seasons.claimLevelAsync(player.id(), SEASON, 2), TIMEOUT, context);
                assertTrue(
                        StressTestSupport.await(rewards.markSettledAsync(seasonClaim.claimId()), TIMEOUT, context),
                        context::description);
            });

            var leaderboard =
                    rankings.topAsync(RANKING, 1_000).toCompletableFuture().join();
            assertEquals(1_000, leaderboard.entries().size());
            assertTrue(leaderboard.entries().stream().allMatch(entry -> entry.value() == 10));
            assertEquals(
                    1_000,
                    leaderboard.entries().stream()
                            .map(entry -> entry.playerId())
                            .distinct()
                            .count());
            assertTrue(rewards.pendingClaimsAsync(1_000)
                    .toCompletableFuture()
                    .join()
                    .isEmpty());
        } finally {
            rankings.closeAsync().toCompletableFuture().join();
            seasons.closeAsync().toCompletableFuture().join();
            achievements.close();
            quests.closeAsync().toCompletableFuture().join();
            statistics.closeAsync().toCompletableFuture().join();
            rewards.closeAsync().toCompletableFuture().join();
        }
    }

    private static EventBus eventBus() {
        var eventBus = mock(EventBus.class);
        doAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)))
                .when(eventBus)
                .publishAsync(any());
        doAnswer(invocation -> mock(EventSubscription.class)).when(eventBus).subscribe(any(), any());
        return eventBus;
    }

    private static void registerReward(com.cotani.reward.api.RewardService rewards, RewardId id) {
        rewards.register(new RewardDefinition(
                id, Duration.ofDays(1), Duration.ofDays(2), 1, List.of(new CurrencyGrant("coins", BigDecimal.ONE))));
    }
}

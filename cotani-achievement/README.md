# cotani-achievement

Asynchronous player achievements with statistic criteria, idempotent unlocks, reward claims, domain events and SQL
progress persistence.

```java
var achievements = CotaniAchievements.storage(storage, statistics, rewards, eventBus);
var achievementId = AchievementId.of("stone-master");

achievements.register(new AchievementDefinition(
        achievementId,
        List.of(new AchievementCriterion(StatisticId.of("blocks-mined"), 1_000)),
        Optional.of(RewardId.of("stone-master-reward"))));

achievements.evaluateAsync(playerId, achievementId)
        .thenAccept(progress -> {
            if (progress.unlocked()) {
                // Transition to the player's owning thread only when touching Paper objects.
            }
        });
```

Criteria are evaluated against the caller-owned `cotani-statistics` service. All criteria must be satisfied before an
achievement is unlocked. The unlock is stored with optimistic revisions and a stable `RewardClaimId`; repeated
successful evaluations return the original progress without publishing a second unlock event.

The module subscribes to `StatisticChangedEvent` and automatically evaluates achievements that use the changed
statistic. Manual `evaluateAsync(...)` calls remain useful after loading definitions, recovering from an event-bus
failure, or integrating with another progress source.

Definitions are process-local and must be registered again after a restart. Player progress is persistent when using
`CotaniAchievements.storage(...)`; register [`CotaniAchievements.migrations()`](src/main/java/com/cotani/achievement/CotaniAchievements.java)
before starting `CotaniStorage`.

The unlock event is best effort: a failed publication is logged and retried by a later evaluation for that achievement.
The service never stores live Bukkit objects. `claimRewardAsync(...)` delegates to `cotani-reward` using the persisted
claim id, so retrying the same achievement reward remains idempotent. Close the achievement service asynchronously
during plugin shutdown; it unsubscribes its statistic listener but does not own the supplied event, statistics or
reward services.

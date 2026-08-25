# cotani-ranking

Named, bounded player rankings backed by `cotani-statistics`.

The module gives plugins a domain API for registering process-local named ranking views instead of exposing statistic
identifiers through every command, menu or placeholder. Definitions must be registered again after a restart. It does
not duplicate statistic storage: values remain owned by `cotani-statistics`, while this module validates ranking limits
and returns immutable ranking snapshots.

```java
var rankings = CotaniRankings.fromStatistics(statistics);
var rankingId = RankingId.of("blocks-mined");

rankings.register(new RankingDefinition(
        rankingId,
        StatisticId.of("blocks-mined"),
        100));

rankings.topAsync(rankingId, 10).thenAccept(snapshot -> {
    snapshot.entries().forEach(entry -> {
        // Capture UUIDs in the async flow; resolve players on their owning thread.
    });
});
```

Rankings are ordered by value descending and UUID ascending for deterministic ties. The service applies a bounded
visible query timeout and pending-query limit. A timed-out query keeps its pending slot until the underlying statistics
query finishes, preventing slow backends from accumulating unbounded work. The service never stores live Bukkit
objects and does not own the supplied statistics service. Close the ranking service asynchronously during plugin
shutdown; close the statistics service separately.

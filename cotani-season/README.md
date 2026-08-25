<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-season

</div>

Progressão sazonal assíncrona para Paper e Folia, com XP idempotente, níveis cumulativos,
claims de recompensas e persistência SQL.

```java
var seasons = CotaniSeasons.fromRepository(
        new StorageSeasonRepository(storage),
        rewards,
        eventBus);

seasons.register(new SeasonDefinition(
        SeasonId.of("summer-2026"),
        "Summer 2026",
        Instant.parse("2026-06-01T00:00:00Z"),
        Instant.parse("2026-09-01T00:00:00Z"),
        List.of(
                new SeasonLevel(1, 0, RewardId.of("summer-level-1")),
                new SeasonLevel(2, 1_000, RewardId.of("summer-level-2")))));

seasons.addExperienceAsync(playerId, SeasonId.of("summer-2026"), 250, operationId);
seasons.claimLevelAsync(playerId, SeasonId.of("summer-2026"), 1);
```

## Garantias

- progressão é Bukkit-free e recebe apenas `UUID` e valores imutáveis;
- cada grant de XP é aplicado uma única vez pelo `SeasonExperienceId`;
- retries do mesmo grant retornam o mesmo snapshot persistido;
- níveis usam thresholds cumulativos e são protegidos por revisão otimista;
- claims usam uma chave determinística de `RewardClaimId`, permitindo recuperação após falhas;
- eventos são publicados depois da persistência e têm timeout best-effort;
- o ledger de idempotência pode ser limpo com `purgeExperienceOperationsAsync(cutoff)` após a janela de retry desejada;
- mutations do mesmo jogador e temporada são serializadas sem bloquear a thread chamadora;
- `StorageSeasonRepository.migrations()` deve ser registrado antes do startup do `CotaniStorage`.

O `RewardService` continua responsável por cooldown, grants e settlement. O `cotani-season` apenas
determina quando um nível foi desbloqueado e solicita o claim idempotente.

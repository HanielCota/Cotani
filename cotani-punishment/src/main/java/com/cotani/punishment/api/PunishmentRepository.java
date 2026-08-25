package com.cotani.punishment.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Asynchronous persistence boundary for immutable punishments. */
public interface PunishmentRepository {
    /** Finds one punishment by its stable ID. */
    CompletionStage<Optional<Punishment>> findAsync(PunishmentId id);

    /** Queries one bounded page; filters and pagination must be applied by the repository. */
    CompletionStage<List<Punishment>> queryAsync(PunishmentQuery query);

    /** Persists an immutable punishment idempotently and rejects conflicting data for the same ID. */
    CompletionStage<Void> saveAsync(Punishment punishment);
}

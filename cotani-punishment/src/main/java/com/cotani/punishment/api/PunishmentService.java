package com.cotani.punishment.api;

import com.cotani.AsyncCloseable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Applies, queries and revokes immutable punishments without retaining Bukkit objects. */
public interface PunishmentService extends AsyncCloseable {
    /** Applies a request, returning the existing record when the ID was already accepted. */
    CompletionStage<Punishment> applyAsync(PunishmentRequest request);

    /** Finds the current punishment with the supplied ID, if it exists. */
    CompletionStage<Optional<Punishment>> findAsync(PunishmentId id);

    /** Returns bounded history or active punishments according to the query. */
    CompletionStage<List<Punishment>> queryAsync(PunishmentQuery query);

    /** Revokes a punishment; a missing ID returns {@link Optional#empty()}. */
    CompletionStage<Optional<Punishment>> revokeAsync(PunishmentId id, Punishment.Revocation revocation);

    default CompletionStage<Optional<Punishment>> activeAsync(UUID targetId, PunishmentType type) {
        return activeAtAsync(targetId, type, Instant.now());
    }

    /** Finds the newest punishment active at an explicit point in time. */
    default CompletionStage<Optional<Punishment>> activeAtAsync(UUID targetId, PunishmentType type, Instant at) {
        return queryAsync(PunishmentQuery.builder()
                        .targetId(targetId)
                        .type(type)
                        .activeAt(at)
                        .limit(1)
                        .build())
                .thenApply(values -> values.stream().findFirst());
    }
}

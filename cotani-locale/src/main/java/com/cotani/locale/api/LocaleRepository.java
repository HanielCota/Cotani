package com.cotani.locale.api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

/** Persistence boundary for player locale preferences. */
@NullMarked
public interface LocaleRepository {
    /**
     * Loads an immutable snapshot of player preferences without blocking the caller.
     * Implementations must complete the stage within the timeout supplied to the locale factory.
     */
    CompletionStage<Map<UUID, LocaleId>> loadAsync();

    /** Saves one preference without blocking the caller. */
    CompletionStage<Void> saveAsync(UUID playerId, LocaleId locale);

    /** Removes one preference without blocking the caller. */
    CompletionStage<Void> deleteAsync(UUID playerId);
}

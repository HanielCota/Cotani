package com.cotani.location.api;

import com.cotani.teleport.api.TeleportOptions;
import com.cotani.teleport.api.TeleportResult;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Resolves saved positions on the correct server thread and delegates to cotani-teleport.
 *
 * <p>The API accepts immutable identifiers only. Missing locations and unloaded worlds complete exceptionally;
 * teleport failures are returned by the underlying {@link TeleportResult} contract.
 */
public interface LocationTeleportService {
    /** Resolves and teleports a player to one of their homes. */
    CompletionStage<TeleportResult> teleportHomeAsync(UUID playerId, LocationName name, TeleportOptions options);

    /** Resolves and teleports a player to a global warp. */
    CompletionStage<TeleportResult> teleportWarpAsync(UUID playerId, LocationName name, TeleportOptions options);
}

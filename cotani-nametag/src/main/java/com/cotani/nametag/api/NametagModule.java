package com.cotani.nametag.api;

import com.cotani.AsyncCloseable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Service contract for managing player nametags, tablist sorting priority, and scoreboard team rules.
 */
public interface NametagModule extends AutoCloseable, AsyncCloseable {

    /**
     * Applies a global nametag to a player.
     *
     * <p>All online viewers will observe this nametag for the player, unless an individual viewer override
     * or custom {@link NametagProvider} takes precedence.
     *
     * @param player the target player
     * @param nametag the nametag to apply
     */
    void apply(Player player, Nametag nametag);

    /**
     * Applies a global nametag to a player by UUID.
     *
     * @param playerId the target player UUID
     * @param nametag the nametag to apply
     */
    void apply(UUID playerId, Nametag nametag);

    /**
     * Applies global nametags in batch for multiple players.
     *
     * <p>Executes a single unified update dispatch across all online viewers instead of scheduling
     * separate tasks per player.
     *
     * @param batch map of player UUIDs to their corresponding nametags
     */
    void applyBatch(Map<UUID, Nametag> batch);

    /**
     * Applies a viewer-specific nametag override for a target player.
     *
     * @param viewer the viewer observing the nametag
     * @param target the target player whose nametag is customized
     * @param nametag the custom nametag
     */
    void applyForViewer(Player viewer, Player target, Nametag nametag);

    /**
     * Applies a viewer-specific nametag override for a target player by UUID.
     *
     * @param viewerId the viewer player UUID
     * @param targetId the target player UUID
     * @param nametag the custom nametag
     */
    void applyForViewer(UUID viewerId, UUID targetId, Nametag nametag);

    /**
     * Resets the global nametag of a player to the default empty nametag.
     *
     * @param player the player to reset
     */
    void reset(Player player);

    /**
     * Resets the global nametag of a player by UUID to the default empty nametag.
     *
     * @param playerId the player UUID to reset
     */
    void reset(UUID playerId);

    /**
     * Resets global nametags in batch for multiple players.
     *
     * @param playerIds collection of player UUIDs to reset
     */
    void resetBatch(Collection<UUID> playerIds);

    /**
     * Resets any viewer-specific override for a target player.
     *
     * @param viewer the viewer player
     * @param target the target player
     */
    void resetForViewer(Player viewer, Player target);

    /**
     * Resets any viewer-specific override for a target player by UUID.
     *
     * @param viewerId the viewer player UUID
     * @param targetId the target player UUID
     */
    void resetForViewer(UUID viewerId, UUID targetId);

    /**
     * Resets all tags for a player, including their global tag and any viewer-specific overrides.
     *
     * @param player the player
     */
    void resetAll(Player player);

    /**
     * Resets all tags for a player by UUID.
     *
     * @param playerId the player UUID
     */
    void resetAll(UUID playerId);

    /**
     * Gets the configured global nametag of a player, if present.
     *
     * @param player the player
     * @return an Optional containing the global nametag, or empty
     */
    Optional<Nametag> getNametag(Player player);

    /**
     * Gets the configured global nametag of a player by UUID, if present.
     *
     * @param playerId the player UUID
     * @return an Optional containing the global nametag, or empty
     */
    Optional<Nametag> getNametag(UUID playerId);

    /**
     * Resolves the effective nametag that a specific viewer sees for a target player.
     *
     * @param viewer the viewer observing the target
     * @param target the target player
     * @return the effective nametag
     */
    Nametag getEffectiveNametag(Player viewer, Player target);

    /**
     * Resolves the effective nametag that a specific viewer sees for a target player by UUID.
     *
     * @param viewerId the viewer player UUID
     * @param targetId the target player UUID
     * @return the effective nametag
     */
    Nametag getEffectiveNametag(UUID viewerId, UUID targetId);

    /**
     * Returns an immutable copy of all registered global nametags.
     *
     * @return map of player UUID to global nametag
     */
    Map<UUID, Nametag> globalNametags();

    /**
     * Registers a dynamic {@link NametagProvider}.
     *
     * @param provider the provider to register
     */
    void registerProvider(NametagProvider provider);

    /**
     * Unregisters a previously registered {@link NametagProvider}.
     *
     * @param provider the provider to unregister
     */
    void unregisterProvider(NametagProvider provider);

    /**
     * Refreshes all scoreboards for all online players.
     */
    void refreshAll();

    /**
     * Refreshes scoreboards for a specific player (both as a viewer and target).
     *
     * @param player the player to refresh
     */
    void refresh(Player player);

    /**
     * Refreshes scoreboards for a specific player by UUID.
     *
     * @param playerId the player UUID to refresh
     */
    void refresh(UUID playerId);

    /**
     * Closes the module, clearing all teams and unregistering listeners.
     */
    @Override
    void close();
}

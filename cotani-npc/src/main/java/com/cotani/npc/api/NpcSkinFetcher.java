package com.cotani.npc.api;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Service for asynchronously fetching player skins and Mojang texture signatures.
 */
public interface NpcSkinFetcher {

    /**
     * Asynchronously fetches the skin for a player by Minecraft username.
     *
     * @param username Minecraft username
     * @return CompletionStage containing the resolved NpcSkin, or empty if not found or on network failure
     */
    CompletionStage<Optional<NpcSkin>> fetchByUsernameAsync(String username);

    /**
     * Asynchronously fetches the skin for a player by Mojang UUID string.
     *
     * @param mojangUuid Mojang UUID (with or without dashes)
     * @return CompletionStage containing the resolved NpcSkin, or empty if not found or on network failure
     */
    CompletionStage<Optional<NpcSkin>> fetchByUuidAsync(String mojangUuid);
}

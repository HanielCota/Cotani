package com.cotani.dialog.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.bukkit.entity.Player;

/**
 * Contextual state provided during prompt evaluation and validation.
 *
 * @param player target player
 * @param attempt attempt counter (starting at 1)
 * @param startedAt timestamp when prompt began
 */
public record PromptContext(Player player, int attempt, Instant startedAt) {

    public PromptContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(startedAt, "startedAt");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
    }

    /**
     * Calculates the duration elapsed since this prompt was opened.
     *
     * @return elapsed duration
     */
    public Duration elapsed() {
        return Duration.between(startedAt, Instant.now());
    }
}

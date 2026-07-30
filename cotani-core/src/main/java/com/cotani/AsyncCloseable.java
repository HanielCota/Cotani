package com.cotani;

import java.util.concurrent.CompletionStage;

/**
 * Owns resources that can be closed without blocking the calling thread.
 *
 * <p>The first call begins closing. Concurrent and later calls must observe the same logical
 * completion and must not repeat resource cleanup. Implementations document how new work is
 * rejected once closing begins.
 */
public interface AsyncCloseable {

    /**
     * Begins non-blocking resource shutdown.
     *
     * @return a stage that completes after all owned resources have closed
     */
    CompletionStage<Void> closeAsync();
}

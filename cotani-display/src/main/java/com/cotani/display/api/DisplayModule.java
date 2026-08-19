package com.cotani.display.api;

import com.cotani.AsyncCloseable;
import java.util.concurrent.CompletionStage;

/**
 * Top-level contract for the Cotani Display module lifecycle and services.
 */
public interface DisplayModule extends AutoCloseable, AsyncCloseable {

    /**
     * Returns the {@link HologramService} instance.
     *
     * @return the hologram service
     */
    HologramService holograms();

    /**
     * Asynchronously closes the module and destroys all spawned holograms.
     *
     * @return a completion stage for when teardown is finished
     */
    @Override
    CompletionStage<Void> closeAsync();

    /**
     * Synchronously closes the module.
     */
    @Override
    void close();
}

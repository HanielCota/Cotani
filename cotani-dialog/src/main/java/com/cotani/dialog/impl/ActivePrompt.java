package com.cotani.dialog.impl;

import com.cotani.api.InternalApi;
import com.cotani.dialog.api.CancelReason;
import java.util.UUID;

/**
 * Internal tracking contract for an ongoing prompt session.
 */
@InternalApi
public interface ActivePrompt {

    /**
     * Unique ID of the target player.
     *
     * @return player UUID
     */
    UUID playerId();

    /**
     * Cancels this prompt with the given reason.
     *
     * @param reason cancellation reason
     */
    void cancel(CancelReason reason);
}

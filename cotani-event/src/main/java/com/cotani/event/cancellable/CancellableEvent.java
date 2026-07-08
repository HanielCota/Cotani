package com.cotani.event.cancellable;

import com.cotani.event.api.CotaniEvent;

/**
 * Represents an event that can prevent an action from continuing.
 *
 * <p>Use cancellable events mainly for pre-action events, such as
 * TransactionPreEvent or TeleportPreEvent.</p>
 */
public interface CancellableEvent extends CotaniEvent {

    boolean cancelled();

    void cancel();

    void uncancel();
}

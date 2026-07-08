package com.cotani.event.cancellable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation for cancellable events.
 */
public abstract class AbstractCancellableEvent implements CancellableEvent {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public final boolean cancelled() {
        return cancelled.get();
    }

    @Override
    public final void cancel() {
        cancelled.set(true);
    }

    @Override
    public final void uncancel() {
        cancelled.set(false);
    }
}

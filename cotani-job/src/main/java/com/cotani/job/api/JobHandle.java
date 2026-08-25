package com.cotani.job.api;

/** Controls one logical job without exposing scheduler implementation details. */
public interface JobHandle {
    JobId id();

    /**
     * Requests local cancellation without waiting for durable removal.
     *
     * <p>If the handler is already running, cancellation is best effort and the durable record is
     * retained until that invocation finishes.
     */
    boolean cancel();

    boolean cancelled();
}

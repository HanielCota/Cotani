package com.cotani.event.api;

import java.time.Duration;
import java.util.Objects;

/** Guardrails for isolated asynchronous listener dispatch. */
public record EventDispatchPolicy(Duration listenerTimeout, boolean unsubscribeOnTimeout) {
    public EventDispatchPolicy {
        Objects.requireNonNull(listenerTimeout, "listenerTimeout");

        if (!listenerTimeout.isPositive()) {
            throw new IllegalArgumentException("listenerTimeout must be positive");
        }
    }

    public static EventDispatchPolicy defaults() {
        return new EventDispatchPolicy(Duration.ofSeconds(5), true);
    }
}

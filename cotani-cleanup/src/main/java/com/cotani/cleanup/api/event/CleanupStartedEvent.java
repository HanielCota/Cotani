package com.cotani.cleanup.api.event;

import com.cotani.cleanup.api.CleanupMode;
import com.cotani.cleanup.api.CleanupRequest;
import com.cotani.event.api.CotaniEvent;
import java.util.Objects;

/** Published when a cleanup request begins scanning. */
public record CleanupStartedEvent(CleanupRequest request, CleanupMode mode) implements CotaniEvent {
    public CleanupStartedEvent {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(mode, "mode");
    }
}

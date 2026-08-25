package com.cotani.cleanup.api.event;

import com.cotani.cleanup.api.CleanupReport;
import com.cotani.event.api.CotaniEvent;
import java.util.Objects;

/** Published after a preview or cleanup report has completed. */
public record CleanupCompletedEvent(CleanupReport report) implements CotaniEvent {
    public CleanupCompletedEvent {
        Objects.requireNonNull(report, "report");
    }
}

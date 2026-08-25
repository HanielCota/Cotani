package com.cotani.cleanup.api;

import com.cotani.AsyncCloseable;
import java.util.concurrent.CompletionStage;

/** Public asynchronous cleanup service with preview, execution and lifecycle control. */
public interface CleanupService extends AsyncCloseable, AutoCloseable {
    /** Creates a request using the service clock and an immutable request timestamp. */
    CleanupRequest newRequest(CleanupPolicy policy, String reason);

    CompletionStage<CleanupReport> previewAsync(CleanupRequest request);

    CompletionStage<CleanupReport> executeAsync(CleanupRequest request);

    default CompletionStage<CleanupReport> previewAsync(CleanupPolicy policy) {
        return previewAsync(newRequest(policy, "preview"));
    }

    default CompletionStage<CleanupReport> executeAsync(CleanupPolicy policy) {
        return executeAsync(newRequest(policy, "manual"));
    }

    /** Starts shutdown and waits asynchronously for accepted operations to finish. */
    @Override
    CompletionStage<Void> closeAsync();

    @Override
    void close();
}

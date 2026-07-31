package com.cotani.task.impl.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ChainLifecycleCallbacksTest {
    @Test
    void completionCallbackCannotReplaceOriginalSuccess() {
        var source = new CompletableFuture<String>();
        ChainLifecycleCallbacks.onComplete(source, () -> {
            throw new IllegalStateException("observer failed");
        });

        source.complete("ok");

        assertEquals("ok", source.getNow(null));
    }

    @Test
    void cancellationCallbackRunsOnlyForCancellation() {
        var source = new CompletableFuture<String>();
        var called = new AtomicBoolean();
        ChainLifecycleCallbacks.onCancel(source, () -> called.set(true));

        source.cancel(true);

        assertTrue(called.get());
    }
}

package com.cotani.task.impl.chain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class TaskTimeoutControllerTest {

    @Test
    void timeoutNeverCompletesTheSourceFuture() {
        var source = new CompletableFuture<String>();
        var controller = new TaskTimeoutController();

        var timed = controller.apply(source, Duration.ofMillis(1));

        assertThrows(CompletionException.class, timed::join);
        assertFalse(source.isDone());
    }

    @Test
    void preservesFailuresThatAreNotTimeouts() {
        var failure = new IllegalStateException("repository failed");
        var controller = new TaskTimeoutController();

        var timed = controller.apply(CompletableFuture.failedFuture(failure), Duration.ofSeconds(1));
        var completion = assertThrows(CompletionException.class, timed::join);

        assertSame(failure, completion.getCause());
    }
}

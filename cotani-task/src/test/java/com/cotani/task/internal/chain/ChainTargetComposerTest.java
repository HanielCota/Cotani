package com.cotani.task.internal.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class ChainTargetComposerTest {
    @Test
    @SuppressWarnings("unchecked")
    void composesCurrentAndRepeatableAttemptsThroughTheSameTarget() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        when(scheduler.supply(any(ExecutionTarget.class), anyString(), any(Supplier.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(
                        invocation.getArgument(2, Supplier.class).get()));
        var state =
                ChainState.repeatable(CompletableFuture.completedFuture(2), () -> CompletableFuture.completedFuture(3));
        var composer = new ChainTargetComposer(scheduler);

        var composed = composer.thenTarget(state, ExecutionTarget.global(), "double", value -> value * 2);

        assertEquals(4, composed.future().getNow(null));
        assertEquals(6, composed.newAttempt().getNow(null));
        verify(scheduler, times(2))
                .supply(any(ExecutionTarget.class), ArgumentMatchers.eq("double"), any(Supplier.class));
    }
}

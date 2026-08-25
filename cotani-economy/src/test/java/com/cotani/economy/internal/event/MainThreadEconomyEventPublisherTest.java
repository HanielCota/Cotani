package com.cotani.economy.internal.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.transaction.EconomyBalanceChange;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionDetails;
import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.SchedulerTask;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class MainThreadEconomyEventPublisherTest {

    @Test
    void shouldPublishEventOnGlobalTargetThroughScheduler() {
        var scheduler = new RecordingScheduler();
        var delegate = mock(EconomyEventPublisher.class);
        var publisher = new MainThreadEconomyEventPublisher(scheduler, delegate, Logger.getLogger("test"));
        var event = new EconomyTransactionEvent(sampleTransaction());

        publisher.publish(event);

        assertSame(ExecutionTarget.global(), scheduler.target);
        assertEquals("economy-event", scheduler.name);
        verify(delegate).publish(event);
    }

    @Test
    void shouldDispatchThroughSchedulerInsteadOfCallingDelegateDirectly() {
        var scheduler = new DeferredScheduler();
        var delegate = mock(EconomyEventPublisher.class);
        var publisher = new MainThreadEconomyEventPublisher(scheduler, delegate, Logger.getLogger("test"));

        publisher.publish(new EconomyTransactionEvent(sampleTransaction()));

        assertEquals(1, scheduler.submissions);
        verify(delegate, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotThrowWhenScheduledPublicationFails() {
        var scheduler = new FailingScheduler();
        var delegate = mock(EconomyEventPublisher.class);
        var publisher = new MainThreadEconomyEventPublisher(scheduler, delegate, Logger.getLogger("test"));

        assertDoesNotThrow(() -> publisher.publish(new EconomyTransactionEvent(sampleTransaction())));

        verify(delegate, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullEvent() {
        var scheduler = new RecordingScheduler();
        var publisher = new MainThreadEconomyEventPublisher(
                scheduler, mock(EconomyEventPublisher.class), Logger.getLogger("test"));

        assertThrows(NullPointerException.class, () -> publisher.publish(null));
        assertEquals(0, scheduler.submissions);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullSchedulerDelegateAndLogger() {
        assertThrows(
                NullPointerException.class,
                () -> new MainThreadEconomyEventPublisher(
                        null, mock(EconomyEventPublisher.class), Logger.getLogger("test")));
        assertThrows(
                NullPointerException.class,
                () -> new MainThreadEconomyEventPublisher(new RecordingScheduler(), null, Logger.getLogger("test")));
        assertThrows(
                NullPointerException.class,
                () -> new MainThreadEconomyEventPublisher(
                        new RecordingScheduler(), mock(EconomyEventPublisher.class), null));
    }

    private static EconomyTransaction sampleTransaction() {
        return EconomyTransaction.deposit(
                new EconomyTransactionDetails(
                        EconomyOperationId.random(),
                        CurrencyId.of("coins"),
                        BigDecimal.TEN,
                        EconomyReason.system("test"),
                        Instant.now()),
                new EconomyBalanceChange(UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.TEN));
    }

    private static final class RecordingScheduler implements AsyncTaskExecutor {
        private ExecutionTarget target = ExecutionTarget.async();
        private String name = "unset";
        private int submissions;

        @Override
        public SchedulerTask async(Runnable runnable) {
            return SchedulerTask.noop();
        }

        @Override
        public SchedulerTask async(String name, Runnable runnable) {
            return SchedulerTask.noop();
        }

        @Override
        public <T> CompletionStage<T> supply(ExecutionTarget target, String name, Supplier<T> supplier) {
            this.target = target;
            this.name = name;
            this.submissions++;
            return CompletableFuture.supplyAsync(supplier, Runnable::run);
        }

        @Override
        public Executor asyncExecutor() {
            return Runnable::run;
        }
    }

    private static final class DeferredScheduler implements AsyncTaskExecutor {
        private int submissions;

        @Override
        public SchedulerTask async(Runnable runnable) {
            return SchedulerTask.noop();
        }

        @Override
        public SchedulerTask async(String name, Runnable runnable) {
            return SchedulerTask.noop();
        }

        @Override
        public <T> CompletionStage<T> supply(ExecutionTarget target, String name, Supplier<T> supplier) {
            submissions++;
            return new CompletableFuture<>();
        }

        @Override
        public Executor asyncExecutor() {
            return Runnable::run;
        }
    }

    private static final class FailingScheduler implements AsyncTaskExecutor {
        @Override
        public SchedulerTask async(Runnable runnable) {
            return SchedulerTask.noop();
        }

        @Override
        public SchedulerTask async(String name, Runnable runnable) {
            return SchedulerTask.noop();
        }

        @Override
        public <T> CompletionStage<T> supply(ExecutionTarget target, String name, Supplier<T> supplier) {
            return CompletableFuture.failedFuture(new IllegalStateException("scheduler down"));
        }

        @Override
        public Executor asyncExecutor() {
            return Runnable::run;
        }
    }
}

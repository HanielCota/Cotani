package com.cotani.teleport.pending;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.task.api.SchedulerTask;
import com.cotani.teleport.api.PendingTeleportState;
import com.cotani.teleport.api.TeleportCancelReason;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import java.time.Duration;
import java.util.UUID;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendingTeleportStateMachineTest {

    private PendingTeleportStateMachine machine;

    @BeforeEach
    @SuppressWarnings("NullAway")
    void setUp() {
        var data = PendingTeleportData.create(
                UUID.randomUUID(),
                new Location(null, 0, 0, 0),
                Duration.ofSeconds(5),
                TeleportOptions.defaults(),
                TeleportCause.PLUGIN_INTERNAL,
                "test");
        machine = new PendingTeleportStateMachine(data);
    }

    @Test
    void initialStateIsWaiting() {
        assertEquals(PendingTeleportState.WAITING, machine.state());
    }

    @Test
    void markExecutingReturnsTrue() {
        assertTrue(machine.markExecuting());
        assertEquals(PendingTeleportState.EXECUTING, machine.state());
    }

    @Test
    void markExecutingFailsIfNotWaiting() {
        machine.markExecuting();
        assertFalse(machine.markExecuting());
    }

    @Test
    void markCompletedSucceedsFromExecuting() {
        machine.markExecuting();
        assertTrue(machine.markCompleted());
        assertEquals(PendingTeleportState.COMPLETED, machine.state());
    }

    @Test
    void markCompletedFailsFromWaiting() {
        assertFalse(machine.markCompleted());
    }

    @Test
    void cancelFromWaitingReturnsTrue() {
        assertTrue(machine.cancel(TeleportCancelReason.QUIT));
        assertEquals(PendingTeleportState.CANCELLED, machine.state());
        assertEquals(TeleportCancelReason.QUIT, machine.cancelReason().orElseThrow());
    }

    @Test
    void cancelFromExecutingReturnsFalse() {
        machine.markExecuting();
        assertFalse(machine.cancel(TeleportCancelReason.QUIT));
    }

    @Test
    void cancelExecutionFromExecutingReturnsTrue() {
        machine.markExecuting();
        assertTrue(machine.cancelExecution(TeleportCancelReason.EXECUTION_FAILED));
        assertEquals(PendingTeleportState.CANCELLED, machine.state());
    }

    @Test
    void cancelReasonEmptyWhenNeverCancelled() {
        assertTrue(machine.cancelReason().isEmpty());
    }

    @Test
    void stateReturnsNonNull() {
        assertNotNull(machine.state());
    }

    @Test
    void attachTaskCancelsIfNotWaiting() {
        machine.markExecuting();
        var task = new SchedulerTask() {
            private boolean cancelled;

            @Override
            public boolean cancel() {
                cancelled = true;
                return true;
            }

            @Override
            public boolean cancelled() {
                return cancelled;
            }
        };
        machine.attachTask(task);
        assertTrue(task.cancelled());
    }
}

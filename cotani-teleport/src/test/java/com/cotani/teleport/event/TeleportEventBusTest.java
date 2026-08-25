package com.cotani.teleport.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TeleportEventBusTest {
    private TeleportEventBus eventBus;
    private UUID entityId;

    private static Event createEvent() {
        return new Event() {
            private static final HandlerList HANDLERS = new HandlerList();

            @Override
            public HandlerList getHandlers() {
                return HANDLERS;
            }
        };
    }

    @BeforeEach
    void setUp() {
        var scheduler = Mockito.mock(PaperTaskScheduler.class);
        Mockito.when(scheduler.supply(Mockito.any(), Mockito.anyString(), Mockito.<Supplier<Void>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        eventBus = new TeleportEventBus(scheduler);
        entityId = UUID.randomUUID();
    }

    @Test
    void callAsyncWithEntityCompletes() {
        var event = createEvent();
        var future = eventBus.callOnEntityAsync(entityId, () -> event);
        assertDoesNotThrow(() -> future.toCompletableFuture().join());
    }

    @Test
    void callAsyncWithoutEntityCompletes() {
        var event = createEvent();
        var future = eventBus.callOnGlobalAsync(() -> event);
        assertDoesNotThrow(() -> future.toCompletableFuture().join());
    }
}

package com.cotani.teleport.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeleportEventBusTest {

    private TeleportEventBus eventBus;
    private Entity entity;

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
        var scheduler = org.mockito.Mockito.mock(PaperTaskScheduler.class);
        org.mockito.Mockito.when(scheduler.supply(
                        org.mockito.Mockito.any(),
                        org.mockito.Mockito.anyString(),
                        org.mockito.Mockito.<Supplier<Void>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        eventBus = new TeleportEventBus(scheduler);
        entity = org.mockito.Mockito.mock(Entity.class);
        org.mockito.Mockito.when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void callAsyncWithEntityCompletes() {
        var event = createEvent();
        var future = eventBus.callAsync(event, entity);
        assertDoesNotThrow(() -> future.toCompletableFuture().join());
    }

    @Test
    void callAsyncWithoutEntityCompletes() {
        var event = createEvent();
        var future = eventBus.callAsync(event);
        assertDoesNotThrow(() -> future.toCompletableFuture().join());
    }
}

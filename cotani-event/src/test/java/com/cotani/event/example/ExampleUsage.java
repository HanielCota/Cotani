package com.cotani.event.example;

import com.cotani.event.api.EventBus;
import com.cotani.event.bus.DefaultEventBus;
import com.cotani.event.exception.LoggingEventExceptionHandler;
import java.util.UUID;

public final class ExampleUsage {

    private ExampleUsage() {
        throw new UnsupportedOperationException("utility class");
    }

    public static void main(String[] args) {
        EventBus eventBus = DefaultEventBus.create(LoggingEventExceptionHandler.usingJavaLogger(), Runnable::run);

        eventBus.subscribe(UserLoadedEvent.class, event -> {
            System.out.println("Loaded user: " + event.userId());
        });

        eventBus.publish(new UserLoadedEvent(UUID.randomUUID()));
    }
}

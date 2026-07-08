package com.cotani.event.exception;

@FunctionalInterface
public interface EventExceptionHandler {

    void handle(EventListenerException exception);
}

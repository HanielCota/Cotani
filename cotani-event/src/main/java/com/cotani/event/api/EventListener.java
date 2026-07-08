package com.cotani.event.api;

@FunctionalInterface
public interface EventListener<T extends CotaniEvent> {

    void handle(T event);
}

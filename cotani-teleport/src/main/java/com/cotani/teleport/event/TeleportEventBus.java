package com.cotani.teleport.event;

import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.util.VoidResult;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;

public final class TeleportEventBus {
    private static final String EVENT_PARAM = "event";

    private final AsyncTaskExecutor scheduler;

    public TeleportEventBus(AsyncTaskExecutor scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Schedules event construction and dispatch on the owning entity thread.
     *
     * @param ownerId entity UUID that owns the event
     * @param eventFactory event factory invoked on the owning entity thread
     * @return completion stage for the dispatch
     */
    public CompletionStage<Void> callOnEntityAsync(UUID ownerId, Supplier<? extends Event> eventFactory) {
        return dispatchOnEntityAsync(ownerId, eventFactory).thenApply(_ -> null);
    }

    /**
     * Creates, dispatches and returns an event on the owning entity thread.
     *
     * @param ownerId entity UUID that owns the event
     * @param eventFactory event factory invoked on the owning entity thread
     * @param <T> event type
     * @return completion stage yielding the dispatched event
     */
    public <T extends Event> CompletionStage<T> dispatchOnEntityAsync(
            UUID ownerId, Supplier<? extends T> eventFactory) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(eventFactory, "eventFactory");

        return scheduler.supply(ExecutionTarget.entity(ownerId), "teleport-event", () -> {
            T event = requireEvent(eventFactory.get());
            call(event);
            return event;
        });
    }

    /**
     * Resolves an optional event and dispatches it on the owning entity thread.
     *
     * @param ownerId entity UUID that owns the event
     * @param eventFactory event factory invoked on the owning entity thread
     * @param <T> event type
     * @return completion stage yielding the event when the factory produced one
     */
    public <T extends Event> CompletionStage<Optional<T>> dispatchIfPresentOnEntityAsync(
            UUID ownerId, Supplier<@Nullable T> eventFactory) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(eventFactory, "eventFactory");

        return scheduler.supply(ExecutionTarget.entity(ownerId), "teleport-event", () -> {
            T event = eventFactory.get();
            if (event == null) {
                return Optional.empty();
            }
            call(event);
            return Optional.of(event);
        });
    }

    /**
     * Compatibility bridge for callers that already own the entity boundary.
     * Prefer {@link #callOnEntityAsync(UUID, Supplier)} so the event is created on the target thread.
     *
     * @deprecated use {@link #callOnEntityAsync(UUID, Supplier)}
     */
    @Deprecated
    public CompletionStage<Void> callAsync(Event event, Entity owner) {
        Objects.requireNonNull(event, EVENT_PARAM);
        Objects.requireNonNull(owner, "owner");

        return callOnEntityAsync(owner.getUniqueId(), () -> event);
    }

    /**
     * Schedules event construction and dispatch on the global thread.
     *
     * @param eventFactory event factory invoked on the global thread
     * @return completion stage for the dispatch
     */
    public CompletionStage<Void> callOnGlobalAsync(Supplier<? extends Event> eventFactory) {
        Objects.requireNonNull(eventFactory, "eventFactory");

        return scheduler.supply(ExecutionTarget.global(), "teleport-event", () -> {
            call(requireEvent(eventFactory.get()));
            return VoidResult.nullValue();
        });
    }

    /**
     * Compatibility bridge for an already-created event.
     *
     * @deprecated use {@link #callOnGlobalAsync(Supplier)}
     */
    @Deprecated
    public CompletionStage<Void> callAsync(Event event) {
        Objects.requireNonNull(event, EVENT_PARAM);

        return callOnGlobalAsync(() -> event);
    }

    public CotaniPreTeleportEvent callPreTeleportSync(CotaniPreTeleportEvent event) {
        Objects.requireNonNull(event, EVENT_PARAM);

        call(event);

        return event;
    }

    public void call(Event event) {
        Objects.requireNonNull(event, EVENT_PARAM);

        Bukkit.getPluginManager().callEvent(event);
    }

    private static <T extends Event> T requireEvent(T event) {
        return Objects.requireNonNull(event, EVENT_PARAM);
    }
}

package com.cotani.event.api;

import com.cotani.event.subscription.EventSubscription;
import java.util.concurrent.CompletionStage;

public interface EventBus {

    <T extends CotaniEvent> T publish(T event);

    <T extends CotaniEvent> CompletionStage<T> publishAsync(T event);

    <T extends CotaniEvent> EventSubscription subscribe(Class<T> eventType, EventListener<? super T> listener);

    <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, EventListener<? super T> listener);

    <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, boolean ignoreCancelled, EventListener<? super T> listener);

    default <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, boolean ignoreCancelled, EventListener<? super T> listener) {
        return subscribe(eventType, EventPriority.NORMAL, ignoreCancelled, listener);
    }

    void unsubscribe(EventSubscription subscription);

    void clear();
}

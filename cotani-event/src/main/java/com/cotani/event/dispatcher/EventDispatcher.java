package com.cotani.event.dispatcher;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.subscription.EventSubscription;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface EventDispatcher {

    <T extends CotaniEvent> T dispatch(T event, List<EventSubscription> subscriptions);

    default <T extends CotaniEvent> CompletionStage<T> dispatchAsync(T event, List<EventSubscription> subscriptions) {
        return CompletableFuture.completedFuture(dispatch(event, subscriptions));
    }
}

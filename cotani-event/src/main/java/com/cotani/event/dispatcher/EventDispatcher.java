package com.cotani.event.dispatcher;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.subscription.EventSubscription;
import java.util.List;

public interface EventDispatcher {

    <T extends CotaniEvent> T dispatch(T event, List<EventSubscription> subscriptions);
}

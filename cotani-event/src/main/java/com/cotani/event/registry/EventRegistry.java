package com.cotani.event.registry;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.subscription.EventSubscription;
import java.util.List;

public interface EventRegistry {

    void register(EventSubscription subscription);

    void unregister(EventSubscription subscription);

    List<EventSubscription> subscriptionsFor(CotaniEvent event);

    void removeInactive();

    void clear();
}

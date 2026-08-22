package com.cotani.redis.channel;

import com.cotani.AsyncCloseable;
import java.util.concurrent.CompletionStage;

/**
 * Handle to an active Redis channel listener subscription.
 */
public interface ChannelSubscription extends AutoCloseable, AsyncCloseable {

    /**
     * Returns the subscribed channel identifier.
     *
     * @return channel identifier
     */
    ChannelId channelId();

    /**
     * Checks if this subscription is actively receiving messages.
     *
     * @return true if active, false if cancelled/closed
     */
    boolean isActive();

    /**
     * Asynchronously cancels this subscription and unregisters its listener.
     *
     * @return a completion stage that finishes once unsubscription is completed
     */
    CompletionStage<Void> unsubscribeAsync();

    @Override
    default CompletionStage<Void> closeAsync() {
        return unsubscribeAsync();
    }

    @Override
    void close();
}

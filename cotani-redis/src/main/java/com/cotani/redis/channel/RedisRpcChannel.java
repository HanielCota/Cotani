package com.cotani.redis.channel;

import com.cotani.AsyncCloseable;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Asynchronous cross-server Request-Response (RPC) communication channel over Redis Pub/Sub.
 *
 * @param <Q> request payload type
 * @param <R> response payload type
 */
public interface RedisRpcChannel<Q, R> extends AutoCloseable, AsyncCloseable {

    /**
     * Sends a request across the network and asynchronously waits for a response with a timeout.
     *
     * @param request request payload
     * @param timeout maximum time to wait for a reply before failing
     * @return stage completing with response received from remote server
     */
    CompletionStage<R> requestAsync(Q request, Duration timeout);

    /**
     * Registers a responder handler that processes incoming requests on this channel and computes a reply.
     *
     * @param responder function mapping request to asynchronous response stage
     * @return subscription handle to cancel/unregister the responder
     */
    ChannelSubscription respond(Function<Q, CompletionStage<R>> responder);

    @Override
    void close();
}

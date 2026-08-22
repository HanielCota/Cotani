package com.cotani.redis.channel;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.redis.CotaniRedis;
import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.internal.DefaultRedisRpcChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RedisRpcChannelTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendRequestAndReceiveResponse() {
        var redis = mock(CotaniRedis.class);
        var reqChannel = mock(RedisChannel.class);
        var inboxChannel = mock(RedisChannel.class);

        var inboxSub = mock(ChannelSubscription.class);
        when(inboxSub.isActive()).thenReturn(true);
        when(inboxSub.unsubscribeAsync()).thenReturn(CompletableFuture.completedFuture(null));

        List<Consumer<byte[]>> inboxListeners = new ArrayList<>();
        when(inboxChannel.subscribe(any())).thenAnswer(inv -> {
            inboxListeners.add(inv.getArgument(0));
            return inboxSub;
        });

        when(redis.channel(any(), any())).thenAnswer(inv -> {
            ChannelId id = inv.getArgument(0);
            if (id.value().startsWith("rpc:inbox:")) {
                return inboxChannel;
            }
            return reqChannel;
        });

        when(reqChannel.publishAsync(any())).thenReturn(CompletableFuture.completedFuture(1L));

        var rpc = new DefaultRedisRpcChannel<String, String>(
                ChannelId.of("rpc:player_info"), RedisCodec.string(), RedisCodec.string(), redis, null, null);

        var future = rpc.requestAsync("player:UUID", Duration.ofSeconds(5));

        // RPC channel is waiting for response
        assertTrue(!future.toCompletableFuture().isDone());
        org.junit.jupiter.api.Assertions.assertEquals(1, inboxListeners.size());

        rpc.close();
    }
}

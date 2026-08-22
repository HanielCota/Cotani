package com.cotani.redis.event;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import com.cotani.event.subscription.EventSubscription;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.ChannelSubscription;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.codec.ByteArrayRedisCodec;
import com.cotani.redis.codec.RedisCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distributed event bus bridging local {@link EventBus} listeners with Redis Pub/Sub across a server cluster.
 */
public final class RedisDistributedEventBus implements EventBus {

    private final EventBus localBus;
    private final RedisChannel<byte[]> redisChannel;
    private final UUID localNodeId;
    private final ChannelSubscription channelSubscription;
    private final Map<String, RedisCodec<? extends CotaniEvent>> eventCodecs = new ConcurrentHashMap<>();

    public RedisDistributedEventBus(EventBus localBus, CotaniRedis redis, ChannelId channelId) {
        this.localBus = Objects.requireNonNull(localBus, "localBus");
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(channelId, "channelId");
        this.localNodeId = UUID.randomUUID();
        this.redisChannel = redis.channel(channelId, ByteArrayRedisCodec.INSTANCE);
        this.channelSubscription = this.redisChannel.subscribe(this::handleIncomingNetworkEvent);
    }

    public <T extends CotaniEvent> RedisDistributedEventBus registerCodec(Class<T> eventClass, RedisCodec<T> codec) {
        Objects.requireNonNull(eventClass, "eventClass");
        Objects.requireNonNull(codec, "codec");
        eventCodecs.put(eventClass.getName(), codec);
        return this;
    }

    @Override
    public <T extends CotaniEvent> T publish(T event) {
        T result = localBus.publish(event);
        publishToNetwork(event);
        return result;
    }

    @Override
    public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
        return localBus.publishAsync(event).thenCompose(result -> {
            var _ = publishToNetwork(event);
            return CompletableFuture.completedFuture(result);
        });
    }

    private <T extends CotaniEvent> CompletionStage<Void> publishToNetwork(T event) {
        String className = event.getClass().getName();
        @SuppressWarnings("unchecked")
        RedisCodec<T> codec = (RedisCodec<T>) eventCodecs.get(className);
        if (codec == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            byte[] eventBytes = Objects.requireNonNull(codec.encode(event), "encoded event");
            var baos = new ByteArrayOutputStream();
            var dos = new DataOutputStream(baos);
            dos.writeLong(localNodeId.getMostSignificantBits());
            dos.writeLong(localNodeId.getLeastSignificantBits());
            dos.writeUTF(className);
            dos.writeInt(eventBytes.length);
            dos.write(eventBytes);
            dos.flush();
            return redisChannel.publishAsync(baos.toByteArray()).thenApply(_ -> null);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void handleIncomingNetworkEvent(byte[] payload) {
        if (payload == null || payload.length < 20) {
            return;
        }
        try {
            var bais = new ByteArrayInputStream(payload);
            var dis = new DataInputStream(bais);
            long most = dis.readLong();
            long least = dis.readLong();
            UUID originNodeId = new UUID(most, least);
            if (localNodeId.equals(originNodeId)) {
                return; // Ignore events originated from this node
            }
            String className = dis.readUTF();
            int len = dis.readInt();
            byte[] eventBytes = new byte[len];
            dis.readFully(eventBytes);

            RedisCodec<?> codec = eventCodecs.get(className);
            if (codec != null) {
                Object decoded = codec.decode(eventBytes);
                if (decoded instanceof CotaniEvent event) {
                    var _ = localBus.publishAsync(event);
                }
            }
        } catch (Exception _) {
            // Ignore corrupted network frame
        }
    }

    @Override
    public <T extends CotaniEvent> EventSubscription subscribe(Class<T> eventType, EventListener<? super T> listener) {
        return localBus.subscribe(eventType, listener);
    }

    @Override
    public <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, EventListener<? super T> listener) {
        return localBus.subscribe(eventType, priority, listener);
    }

    @Override
    public <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, boolean ignoreCancelled, EventListener<? super T> listener) {
        return localBus.subscribe(eventType, priority, ignoreCancelled, listener);
    }

    @Override
    public void unsubscribe(EventSubscription subscription) {
        localBus.unsubscribe(subscription);
    }

    @Override
    public void clear() {
        localBus.clear();
    }

    @Override
    public void close() {
        channelSubscription.close();
        localBus.close();
    }
}

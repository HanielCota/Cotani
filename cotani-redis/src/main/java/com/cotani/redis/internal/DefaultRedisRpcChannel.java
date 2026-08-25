package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.ChannelSubscription;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.channel.RedisRpcChannel;
import com.cotani.redis.codec.ByteArrayRedisCodec;
import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.exception.RedisException;
import com.cotani.redis.exception.RedisTimeoutException;
import com.cotani.task.api.PaperTaskScheduler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultRedisRpcChannel<Q, R> implements RedisRpcChannel<Q, R> {

    private static final Logger LOGGER = Logger.getLogger(DefaultRedisRpcChannel.class.getName());

    private final RedisCodec<Q> requestCodec;
    private final RedisCodec<R> responseCodec;
    private final RedisChannel<byte[]> rawRequestChannel;
    private final ChannelId inboxChannelId;
    private final RedisChannel<byte[]> rawInboxChannel;
    private final CotaniRedis redis;
    private final @Nullable PaperTaskScheduler scheduler;
    private final @Nullable ScheduledExecutorService fallbackExecutor;

    private final ConcurrentHashMap<String, PendingRequest<R>> pendingRequests = new ConcurrentHashMap<>();
    private final ChannelSubscription inboxSubscription;
    private volatile boolean closed;

    private record PendingRequest<T>(
            CompletableFuture<T> future, @Nullable ScheduledFuture<?> scheduledTask) {}

    public DefaultRedisRpcChannel(
            ChannelId channelId,
            RedisCodec<Q> requestCodec,
            RedisCodec<R> responseCodec,
            CotaniRedis redis,
            @Nullable PaperTaskScheduler scheduler,
            @Nullable ScheduledExecutorService fallbackExecutor) {
        Objects.requireNonNull(channelId, "channelId");
        this.requestCodec = Objects.requireNonNull(requestCodec, "requestCodec");
        this.responseCodec = Objects.requireNonNull(responseCodec, "responseCodec");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.scheduler = scheduler;
        this.fallbackExecutor = fallbackExecutor;

        this.rawRequestChannel = redis.channel(channelId, ByteArrayRedisCodec.INSTANCE);

        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.inboxChannelId = ChannelId.of("rpc:inbox:" + randomSuffix);
        this.rawInboxChannel = redis.channel(inboxChannelId, ByteArrayRedisCodec.INSTANCE);

        this.inboxSubscription = this.rawInboxChannel.subscribe(this::handleIncomingResponse);
    }

    @Override
    public CompletionStage<R> requestAsync(Q request, Duration timeout) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(timeout, "timeout");
        if (closed) {
            return CompletableFuture.failedFuture(new RedisException("RPC channel is closed"));
        }

        String correlationId = UUID.randomUUID().toString();
        var future = new CompletableFuture<R>();

        ScheduledFuture<?> timeoutTask = scheduleTimeout(correlationId, timeout);
        pendingRequests.put(correlationId, new PendingRequest<>(future, timeoutTask));

        byte[] requestPayload = Objects.requireNonNull(requestCodec.encode(request), "encoded request");
        byte[] envelope = encodeEnvelope(correlationId, inboxChannelId.value(), requestPayload, false);

        var _ = rawRequestChannel.publishAsync(envelope).whenComplete((_, error) -> {
            if (error != null) {
                var pending = pendingRequests.remove(correlationId);
                if (pending != null) {
                    if (pending.scheduledTask() != null) {
                        pending.scheduledTask().cancel(false);
                    }
                    pending.future().completeExceptionally(error);
                }
            }
        });

        return future;
    }

    private @Nullable ScheduledFuture<?> scheduleTimeout(String correlationId, Duration timeout) {
        Runnable timeoutAction = () -> {
            var pending = pendingRequests.remove(correlationId);
            if (pending != null) {
                pending.future()
                        .completeExceptionally(
                                new RedisTimeoutException("RPC request timed out after " + timeout.toMillis() + "ms"));
            }
        };

        if (fallbackExecutor != null && !fallbackExecutor.isShutdown()) {
            return fallbackExecutor.schedule(timeoutAction, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        if (scheduler != null) {
            var _ = scheduler.asyncLater(timeoutAction, timeout);
        }
        return null;
    }

    @Override
    public ChannelSubscription respond(Function<Q, CompletionStage<R>> responder) {
        Objects.requireNonNull(responder, "responder");
        return rawRequestChannel.subscribe(envelopeBytes -> {
            RpcEnvelope envelope = decodeEnvelope(envelopeBytes);
            if (envelope == null || envelope.replyChannel().isBlank()) {
                return;
            }
            try {
                Q req = Objects.requireNonNull(requestCodec.decode(envelope.payload()), "decoded request");
                CompletionStage<R> stage = responder.apply(req);
                if (stage == null) {
                    byte[] errBytes = "RPC responder returned null stage".getBytes(StandardCharsets.UTF_8);
                    byte[] respEnv = encodeEnvelope(envelope.correlationId(), "", errBytes, true);
                    publishRawReply(envelope.replyChannel(), respEnv);
                    return;
                }
                var _ = stage.whenComplete((res, error) -> {
                    if (error != null) {
                        byte[] errBytes = (error.getMessage() != null ? error.getMessage() : "RPC responder error")
                                .getBytes(StandardCharsets.UTF_8);
                        byte[] respEnv = encodeEnvelope(envelope.correlationId(), "", errBytes, true);
                        publishRawReply(envelope.replyChannel(), respEnv);
                        return;
                    }
                    byte[] resBytes = Objects.requireNonNull(responseCodec.encode(res), "encoded response");
                    byte[] respEnv = encodeEnvelope(envelope.correlationId(), "", resBytes, false);
                    publishRawReply(envelope.replyChannel(), respEnv);
                });
            } catch (Exception ex) {
                byte[] errBytes = (ex.getMessage() != null ? ex.getMessage() : "RPC responder error")
                        .getBytes(StandardCharsets.UTF_8);
                byte[] respEnv = encodeEnvelope(envelope.correlationId(), "", errBytes, true);
                publishRawReply(envelope.replyChannel(), respEnv);
            }
        });
    }

    private void publishRawReply(String replyChannelName, byte[] responseEnvelope) {
        try {
            var replyChan = ChannelId.of(replyChannelName);
            var replyChannel = redis.channel(replyChan, ByteArrayRedisCodec.INSTANCE);
            var _ = replyChannel.publishAsync(responseEnvelope);
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Could not publish Redis RPC reply", exception);
        }
    }

    private void handleIncomingResponse(byte[] envelopeBytes) {
        RpcEnvelope envelope = decodeEnvelope(envelopeBytes);
        if (envelope == null) {
            return;
        }
        var pending = pendingRequests.remove(envelope.correlationId());
        if (pending == null) {
            return;
        }
        if (pending.scheduledTask() != null) {
            pending.scheduledTask().cancel(false);
        }

        if (envelope.error()) {
            String errorMsg = new String(envelope.payload(), StandardCharsets.UTF_8);
            pending.future().completeExceptionally(new RedisException("RPC remote error: " + errorMsg));
            return;
        }

        try {
            R res = Objects.requireNonNull(responseCodec.decode(envelope.payload()), "decoded response");
            pending.future().complete(res);
        } catch (Exception decodeEx) {
            pending.future().completeExceptionally(decodeEx);
        }
    }

    private static byte[] encodeEnvelope(String correlationId, String replyChannel, byte[] payload, boolean isError) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(correlationId);
            dos.writeUTF(replyChannel);
            dos.writeBoolean(isError);
            dos.writeInt(payload.length);
            dos.write(payload);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode RPC envelope", e);
        }
    }

    private static @Nullable RpcEnvelope decodeEnvelope(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            return null;
        }
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bais);
            String corrId = dis.readUTF();
            String replyChan = dis.readUTF();
            boolean error = dis.readBoolean();
            int payloadLen = dis.readInt();
            byte[] payload = new byte[payloadLen];
            dis.readFully(payload);
            return new RpcEnvelope(corrId, replyChan, payload, error);
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Could not decode Redis RPC envelope", exception);
            return null;
        }
    }

    @SuppressWarnings("ArrayRecordComponent")
    private record RpcEnvelope(String correlationId, String replyChannel, byte[] payload, boolean error) {}

    @Override
    public synchronized CompletionStage<Void> closeAsync() {
        if (closed) {
            return CompletableFuture.completedFuture(null);
        }
        closed = true;
        for (var entry : pendingRequests.values()) {
            if (entry.scheduledTask() != null) {
                entry.scheduledTask().cancel(false);
            }
            entry.future().completeExceptionally(new RedisException("RPC channel was closed"));
        }
        pendingRequests.clear();
        return inboxSubscription.unsubscribeAsync();
    }

    @Override
    public void close() {
        closeAsync().whenComplete((_, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close Redis RPC channel: " + inboxChannelId, failure);
            }
        });
    }
}

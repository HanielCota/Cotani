package com.cotani.mail.internal;

import com.cotani.api.InternalApi;
import com.cotani.mail.api.MailExpiredException;
import com.cotani.mail.api.MailId;
import com.cotani.mail.api.MailMessage;
import com.cotani.mail.api.MailPage;
import com.cotani.mail.api.MailQuery;
import com.cotani.mail.api.MailRepository;
import com.cotani.mail.api.MailSendRequest;
import com.cotani.mail.api.MailService;
import com.cotani.mail.api.MailServiceOptions;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultMailService implements MailService {
    private static final Logger LOGGER = Logger.getLogger(DefaultMailService.class.getName());

    private final MailRepository repository;
    private final MailServiceOptions options;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    private DefaultMailService(MailRepository repository, MailServiceOptions options, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DefaultMailService create(MailRepository repository, MailServiceOptions options, Clock clock) {
        return new DefaultMailService(repository, options, clock);
    }

    @Override
    public CompletionStage<MailMessage> sendAsync(MailSendRequest request) {
        Objects.requireNonNull(request, "request");
        return enqueue(() -> {
            var message = request.toMessage();
            if (message.isExpired(clock.instant())) {
                throw new MailExpiredException(message.id(), message.expiresAt());
            }
            return afterDurable(repository.saveAsync(message), saved -> saved);
        });
    }

    @Override
    public CompletionStage<MailMessage> sendAsync(UUID senderId, UUID recipientId, String subject, String body) {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        return sendAsync(new MailSendRequest(
                MailId.random(), senderId, recipientId, subject, body, clock.instant(), options.defaultTimeToLive()));
    }

    @Override
    public CompletionStage<MailPage> inboxAsync(UUID recipientId, MailQuery query) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(query, "query");
        if (query.pageSize() > options.maxPageSize()) {
            return failed(new IllegalArgumentException("pageSize exceeds configured maximum"));
        }
        CompletionStage<Void> pendingMutations;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            pendingMutations = sequencingTail;
        }
        var stage = pendingMutations.thenCompose(ignored -> repository.inboxAsync(recipientId, query, clock.instant()));
        return options.withRepositoryTimeout(stage);
    }

    @Override
    public CompletionStage<MailMessage> markReadAsync(UUID recipientId, MailId id) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(id, "id");
        return enqueue(
                () -> afterDurable(repository.markReadAsync(recipientId, id, clock.instant()), updated -> updated));
    }

    @Override
    public CompletionStage<Void> deleteAsync(UUID recipientId, MailId id) {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(id, "id");
        return enqueue(() -> afterDurableVoid(repository.deleteAsync(recipientId, id), () -> {}));
    }

    @Override
    public CompletionStage<Void> purgeExpiredAsync() {
        return enqueue(() -> afterDurableVoid(repository.purgeExpiredAsync(clock.instant()), () -> {}));
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage = lastOperation;
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close mail service", failure);
            }
        });
    }

    private <T> Mutation<T> afterDurable(CompletionStage<T> durable, Function<T, T> visibleValue) {
        var updated = durable.thenApply(visibleValue);
        return new Mutation<>(options.withRepositoryTimeout(updated), updated.thenApply(ignored -> null));
    }

    private Mutation<Void> afterDurableVoid(CompletionStage<Void> durable, Runnable visibleValue) {
        var updated = durable.thenRun(visibleValue);
        return new Mutation<>(options.withRepositoryTimeout(updated), updated);
    }

    private <T> CompletionStage<T> enqueue(Supplier<Mutation<T>> operation) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }

            var result = new CompletableFuture<T>();
            var barrier = new CompletableFuture<Void>();
            var predecessor = sequencingTail;
            predecessor.whenComplete((ignored, failure) -> {
                Mutation<T> mutation;
                try {
                    mutation = Objects.requireNonNull(operation.get(), "operation");
                } catch (RuntimeException operationFailure) {
                    result.completeExceptionally(operationFailure);
                    barrier.completeExceptionally(operationFailure);
                    return;
                }
                mutation.result().whenComplete((value, operationFailure) -> {
                    if (operationFailure == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(operationFailure);
                    }
                });
                mutation.barrier().whenComplete((value, operationFailure) -> {
                    if (operationFailure == null) {
                        barrier.complete(null);
                    } else {
                        barrier.completeExceptionally(operationFailure);
                    }
                });
            });
            sequencingTail = barrier.handle((ignored, failure) -> null);
            lastOperation = barrier;
            return result;
        }
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Mail service is closed");
    }

    private record Mutation<T>(CompletionStage<T> result, CompletionStage<Void> barrier) {
        private Mutation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(barrier, "barrier");
        }
    }
}

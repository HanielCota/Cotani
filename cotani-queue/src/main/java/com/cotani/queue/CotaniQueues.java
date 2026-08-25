package com.cotani.queue;

import com.cotani.event.api.EventBus;
import com.cotani.queue.api.QueueRepository;
import com.cotani.queue.api.QueueService;
import com.cotani.queue.api.QueueServiceOptions;
import com.cotani.queue.api.QueueSnapshot;
import com.cotani.queue.internal.DefaultQueueService;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** Factories for the {@code cotani-queue} module. */
public final class CotaniQueues {
    private CotaniQueues() {}

    /** Creates an isolated in-memory queue service. */
    public static QueueService inMemory() {
        return create(QueueSnapshot.empty(), null, null, QueueServiceOptions.defaults(), Clock.systemUTC());
    }

    /** Creates an in-memory queue service with optional event publication. */
    public static QueueService inMemory(@Nullable EventBus eventBus) {
        return create(QueueSnapshot.empty(), null, eventBus, QueueServiceOptions.defaults(), Clock.systemUTC());
    }

    /** Restores queue state asynchronously from a repository. */
    public static CompletionStage<QueueService> fromRepositoryAsync(QueueRepository repository) {
        return fromRepositoryAsync(repository, null, QueueServiceOptions.defaults());
    }

    /** Restores queue state with explicit options and optional event publication. */
    public static CompletionStage<QueueService> fromRepositoryAsync(
            QueueRepository repository, @Nullable EventBus eventBus, QueueServiceOptions options) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(options, "options");
        return options.withRepositoryTimeout(Objects.requireNonNull(repository.loadAsync(), "repository load stage"))
                .thenApply(snapshot -> create(snapshot, repository, eventBus, options, Clock.systemUTC()));
    }

    private static QueueService create(
            QueueSnapshot snapshot,
            @Nullable QueueRepository repository,
            @Nullable EventBus eventBus,
            QueueServiceOptions options,
            Clock clock) {
        return new DefaultQueueService(snapshot, repository, eventBus, options, clock);
    }
}

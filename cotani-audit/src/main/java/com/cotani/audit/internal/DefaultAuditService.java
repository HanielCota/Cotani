package com.cotani.audit.internal;

import com.cotani.api.InternalApi;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditQuery;
import com.cotani.audit.api.AuditRepository;
import com.cotani.audit.api.AuditService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

@InternalApi
public final class DefaultAuditService implements AuditService {
    private final AuditRepository repository;
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletionStage<Void> appendTail = completedVoid();

    public DefaultAuditService(AuditRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public CompletionStage<Void> recordAsync(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");

        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }

            appendTail =
                    appendTail.thenCompose(_ -> Objects.requireNonNull(repository.appendAsync(entry), "append stage"));
            return appendTail;
        }
    }

    @Override
    public CompletionStage<List<AuditEntry>> findAsync(AuditQuery query) {
        Objects.requireNonNull(query, "query");

        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }

            return appendTail
                    .thenCompose(_ -> Objects.requireNonNull(repository.queryAsync(query), "query stage"))
                    .thenApply(List::copyOf);
        }
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return appendTail;
            }
            return appendTail;
        }
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    @SuppressWarnings("NullAway")
    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Audit service is closed");
    }
}

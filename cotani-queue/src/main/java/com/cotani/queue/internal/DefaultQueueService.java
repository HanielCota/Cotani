package com.cotani.queue.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.queue.api.QueueConflictException;
import com.cotani.queue.api.QueueEntryOptions;
import com.cotani.queue.api.QueueFullException;
import com.cotani.queue.api.QueueId;
import com.cotani.queue.api.QueueMatch;
import com.cotani.queue.api.QueueRepository;
import com.cotani.queue.api.QueueService;
import com.cotani.queue.api.QueueServiceOptions;
import com.cotani.queue.api.QueueSnapshot;
import com.cotani.queue.api.QueueTicket;
import com.cotani.queue.api.event.QueueEvent;
import com.cotani.queue.api.event.QueueMatchedEvent;
import com.cotani.queue.api.event.QueueTicketDequeuedEvent;
import com.cotani.queue.api.event.QueueTicketEnqueuedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultQueueService implements QueueService {
    private static final Logger LOGGER = Logger.getLogger(DefaultQueueService.class.getName());
    private static final Comparator<QueueTicket> MATCH_ORDER =
            Comparator.comparingInt(QueueTicket::priority).reversed().thenComparingLong(QueueTicket::sequence);

    private final Object stateLock = new Object();
    private final @Nullable QueueRepository repository;
    private final @Nullable EventBus eventBus;
    private final QueueServiceOptions options;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();

    private QueueSnapshot snapshot;
    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    public DefaultQueueService(
            QueueSnapshot initialSnapshot,
            @Nullable QueueRepository repository,
            @Nullable EventBus eventBus,
            QueueServiceOptions options,
            Clock clock) {
        this.snapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
        this.repository = repository;
        this.eventBus = eventBus;
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletionStage<QueueTicket> enqueueAsync(QueueId queueId, UUID playerId, QueueEntryOptions entryOptions) {
        Objects.requireNonNull(queueId, "queueId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(entryOptions, "entryOptions");
        return enqueue(() -> {
            var now = clock.instant();
            QueueSnapshot current;
            synchronized (stateLock) {
                current = snapshot;
            }
            var cleaned = removeExpired(current, now);
            var activeTickets = activeTickets(cleaned, now);
            if (activeTickets.stream().anyMatch(ticket -> ticket.playerId().equals(playerId))) {
                throw new QueueConflictException("player already has an active queue ticket");
            }
            var queueEntries = activeTickets.stream()
                    .filter(ticket -> ticket.queueId().equals(queueId))
                    .count();
            if (queueEntries >= options.maximumEntriesPerQueue()) {
                throw new QueueFullException(queueId);
            }
            var ticket = new QueueTicket(
                    UUID.randomUUID(),
                    queueId,
                    playerId,
                    entryOptions.priority(),
                    now,
                    now.plus(entryOptions.lifetime()),
                    cleaned.nextSequence());
            var tickets = new ArrayList<>(cleaned.tickets());
            tickets.add(ticket);
            var next = nextSnapshot(cleaned, tickets, cleaned.nextSequence() + 1);
            return commit(next, List.of(new QueueTicketEnqueuedEvent(ticket))).thenApply(ignored -> ticket);
        });
    }

    @Override
    public CompletionStage<Optional<QueueTicket>> dequeueAsync(UUID ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        return enqueue(() -> {
            var now = clock.instant();
            QueueSnapshot current;
            synchronized (stateLock) {
                current = snapshot;
            }
            var cleaned = removeExpired(current, now);
            var ticket = current.tickets().stream()
                    .filter(value -> value.ticketId().equals(ticketId))
                    .findFirst();
            if (ticket.isEmpty()) {
                if (cleaned.tickets().size() == current.tickets().size()) {
                    return completed(Optional.empty());
                }
                var cleanup = nextSnapshot(cleaned, cleaned.tickets(), cleaned.nextSequence());
                return commit(cleanup, List.of()).thenApply(ignored -> Optional.empty());
            }
            var activeTicket = ticket.orElseThrow();
            if (activeTicket.isExpiredAt(now)) {
                var next = nextSnapshot(cleaned, cleaned.tickets(), cleaned.nextSequence());
                return commit(next, List.of()).thenApply(ignored -> Optional.empty());
            }
            var tickets = cleaned.tickets().stream()
                    .filter(value -> !value.ticketId().equals(ticketId))
                    .toList();
            var next = nextSnapshot(cleaned, tickets, cleaned.nextSequence());
            return commit(next, List.of(new QueueTicketDequeuedEvent(activeTicket)))
                    .thenApply(ignored -> Optional.of(activeTicket));
        });
    }

    @Override
    public CompletionStage<Optional<QueueTicket>> findByPlayerAsync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return enqueue(() -> {
            synchronized (stateLock) {
                var now = clock.instant();
                return completed(snapshot.tickets().stream()
                        .filter(ticket -> ticket.playerId().equals(playerId) && !ticket.isExpiredAt(now))
                        .findFirst());
            }
        });
    }

    @Override
    public CompletionStage<List<QueueTicket>> entriesAsync(QueueId queueId) {
        Objects.requireNonNull(queueId, "queueId");
        return enqueue(() -> {
            synchronized (stateLock) {
                return completed(orderedEntries(snapshot, queueId, clock.instant()));
            }
        });
    }

    @Override
    public CompletionStage<Optional<QueueMatch>> matchAsync(QueueId queueId, int requiredPlayers) {
        Objects.requireNonNull(queueId, "queueId");
        if (requiredPlayers < 2) {
            throw new IllegalArgumentException("requiredPlayers must be at least 2");
        }
        if (requiredPlayers > options.maximumEntriesPerQueue()) {
            throw new IllegalArgumentException("requiredPlayers cannot exceed queue capacity");
        }
        return enqueue(() -> {
            var now = clock.instant();
            QueueSnapshot current;
            synchronized (stateLock) {
                current = snapshot;
            }
            var cleaned = removeExpired(current, now);
            var candidates = orderedEntries(cleaned, queueId, now);
            if (candidates.size() < requiredPlayers) {
                if (cleaned.tickets().size() == current.tickets().size()) {
                    return completed(Optional.empty());
                }
                var cleanup = nextSnapshot(cleaned, cleaned.tickets(), cleaned.nextSequence());
                return commit(cleanup, List.of()).thenApply(ignored -> Optional.empty());
            }
            var selected = List.copyOf(candidates.subList(0, requiredPlayers));
            var selectedIds = selected.stream().map(QueueTicket::ticketId).toList();
            var remaining = cleaned.tickets().stream()
                    .filter(ticket -> !selectedIds.contains(ticket.ticketId()))
                    .toList();
            var match = new QueueMatch(UUID.randomUUID(), queueId, selected, now);
            var next = nextSnapshot(cleaned, remaining, cleaned.nextSequence());
            return commit(next, List.of(new QueueMatchedEvent(match))).thenApply(ignored -> Optional.of(match));
        });
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (stateLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage = lastOperation.whenComplete((ignored, failure) -> {
                synchronized (stateLock) {
                    snapshot = QueueSnapshot.empty();
                }
            });
            return closeStage;
        }
    }

    private CompletionStage<Void> commit(QueueSnapshot next, List<QueueEvent> events) {
        return persist(next).thenCompose(ignored -> {
            synchronized (stateLock) {
                snapshot = next;
            }
            CompletionStage<Void> publication = completedVoid();
            for (var event : events) {
                publication = publication.thenCompose(ignoredEvent -> publish(event));
            }
            return publication;
        });
    }

    private CompletionStage<Void> persist(QueueSnapshot next) {
        if (repository == null) {
            return completedVoid();
        }
        return options.withRepositoryTimeout(
                Objects.requireNonNull(repository.saveAsync(next, next.revision() - 1), "repository save stage"));
    }

    private CompletionStage<Void> publish(QueueEvent event) {
        if (eventBus == null) {
            return completedVoid();
        }
        try {
            return options.withEventTimeout(Objects.requireNonNull(eventBus.publishAsync(event), "event stage"))
                    .handle((ignored, failure) -> {
                        if (failure != null) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Queue event publication failed: "
                                            + event.getClass().getName(),
                                    failure);
                        }
                        return null;
                    });
        } catch (RuntimeException failure) {
            LOGGER.log(
                    Level.WARNING,
                    "Queue event publication failed: " + event.getClass().getName(),
                    failure);
            return completedVoid();
        }
    }

    private <T> CompletionStage<T> enqueue(Supplier<CompletionStage<T>> operation) {
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var submitted = sequencingTail.handle((ignored, failure) -> null).thenCompose(ignored -> {
                try {
                    return Objects.requireNonNull(operation.get(), "operation stage");
                } catch (RuntimeException failure) {
                    return failed(failure);
                }
            });
            sequencingTail = submitted.handle((ignored, failure) -> null);
            lastOperation = submitted.thenApply(ignored -> null);
            return submitted;
        }
    }

    private static QueueSnapshot removeExpired(QueueSnapshot current, Instant now) {
        var active = current.tickets().stream()
                .filter(ticket -> !ticket.isExpiredAt(now))
                .toList();
        return nextSnapshot(current, active, current.nextSequence(), false);
    }

    private static List<QueueTicket> activeTickets(QueueSnapshot snapshot, Instant now) {
        return snapshot.tickets().stream()
                .filter(ticket -> !ticket.isExpiredAt(now))
                .toList();
    }

    private static List<QueueTicket> orderedEntries(QueueSnapshot snapshot, QueueId queueId, Instant now) {
        return activeTickets(snapshot, now).stream()
                .filter(ticket -> ticket.queueId().equals(queueId))
                .sorted(MATCH_ORDER)
                .toList();
    }

    private static QueueSnapshot nextSnapshot(QueueSnapshot current, List<QueueTicket> tickets, long nextSequence) {
        return nextSnapshot(current, tickets, nextSequence, true);
    }

    private static QueueSnapshot nextSnapshot(
            QueueSnapshot current, List<QueueTicket> tickets, long nextSequence, boolean incrementRevision) {
        var revision = incrementRevision ? current.revision() + 1 : current.revision();
        return new QueueSnapshot(revision, nextSequence, tickets);
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Queue service is closed");
    }
}

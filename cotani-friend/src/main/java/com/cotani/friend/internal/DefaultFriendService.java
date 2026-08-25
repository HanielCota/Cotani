package com.cotani.friend.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.friend.api.FriendBlock;
import com.cotani.friend.api.FriendBlockedException;
import com.cotani.friend.api.FriendConflictException;
import com.cotani.friend.api.FriendRepository;
import com.cotani.friend.api.FriendRequest;
import com.cotani.friend.api.FriendRequestException;
import com.cotani.friend.api.FriendService;
import com.cotani.friend.api.FriendServiceOptions;
import com.cotani.friend.api.FriendSnapshot;
import com.cotani.friend.api.Friendship;
import com.cotani.friend.api.FriendshipNotFoundException;
import com.cotani.friend.api.event.FriendBlockedEvent;
import com.cotani.friend.api.event.FriendEvent;
import com.cotani.friend.api.event.FriendRequestAcceptedEvent;
import com.cotani.friend.api.event.FriendRequestCancelledEvent;
import com.cotani.friend.api.event.FriendRequestDeclinedEvent;
import com.cotani.friend.api.event.FriendRequestSentEvent;
import com.cotani.friend.api.event.FriendUnblockedEvent;
import com.cotani.friend.api.event.FriendshipRemovedEvent;
import java.time.Clock;
import java.util.ArrayList;
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
public final class DefaultFriendService implements FriendService {
    private static final Logger LOGGER = Logger.getLogger(DefaultFriendService.class.getName());

    private final Object stateLock = new Object();
    private final @Nullable FriendRepository repository;
    private final @Nullable EventBus eventBus;
    private final FriendServiceOptions options;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();

    private FriendSnapshot snapshot;
    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    public DefaultFriendService(
            FriendSnapshot initialSnapshot,
            @Nullable FriendRepository repository,
            @Nullable EventBus eventBus,
            FriendServiceOptions options,
            Clock clock) {
        this.snapshot = Objects.requireNonNull(initialSnapshot, "initialSnapshot");
        this.repository = repository;
        this.eventBus = eventBus;
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletionStage<FriendRequest> sendRequestAsync(UUID requesterId, UUID targetId) {
        requireDifferentPlayers(requesterId, targetId);
        return enqueue(() -> {
            FriendSnapshot current;
            synchronized (stateLock) {
                current = snapshot;
                requireNotBlocked(current, requesterId, targetId);
                if (hasFriendship(current, requesterId, targetId)) {
                    throw new FriendConflictException("players are already friends");
                }
                if (hasRequest(current, requesterId, targetId)) {
                    throw new FriendConflictException("friend request already exists");
                }
                if (hasRequest(current, targetId, requesterId)) {
                    throw new FriendRequestException("an incoming request already exists");
                }
            }
            var request = new FriendRequest(requesterId, targetId, clock.instant());
            var requests = new ArrayList<>(current.requests());
            requests.add(request);
            var next = nextSnapshot(current, current.friendships(), requests, current.blocks());
            return commit(next, List.of(new FriendRequestSentEvent(request))).thenApply(ignored -> request);
        });
    }

    @Override
    public CompletionStage<Friendship> acceptRequestAsync(UUID targetId, UUID requesterId) {
        requireDifferentPlayers(targetId, requesterId);
        return enqueue(() -> {
            FriendSnapshot current;
            synchronized (stateLock) {
                current = snapshot;
                requireNotBlocked(current, targetId, requesterId);
                requireRequest(current, requesterId, targetId);
                if (hasFriendship(current, requesterId, targetId)) {
                    throw new FriendConflictException("players are already friends");
                }
            }
            var friendship = Friendship.create(requesterId, targetId, clock.instant());
            var requests = withoutRequestsBetween(current.requests(), requesterId, targetId);
            var friendships = new ArrayList<>(current.friendships());
            friendships.add(friendship);
            var next = nextSnapshot(current, friendships, requests, current.blocks());
            return commit(next, List.of(new FriendRequestAcceptedEvent(friendship, requesterId, targetId)))
                    .thenApply(ignored -> friendship);
        });
    }

    @Override
    public CompletionStage<Void> declineRequestAsync(UUID targetId, UUID requesterId) {
        requireDifferentPlayers(targetId, requesterId);
        return enqueue(() -> transitionRequest(
                requesterId, targetId, new FriendRequestDeclinedEvent(requesterId, targetId, targetId)));
    }

    @Override
    public CompletionStage<Void> cancelRequestAsync(UUID requesterId, UUID targetId) {
        requireDifferentPlayers(requesterId, targetId);
        return enqueue(
                () -> transitionRequest(requesterId, targetId, new FriendRequestCancelledEvent(requesterId, targetId)));
    }

    @Override
    public CompletionStage<List<Friendship>> friendsAsync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(snapshot.friendships().stream()
                    .filter(friendship -> friendship.contains(playerId))
                    .toList());
        }
    }

    @Override
    public CompletionStage<List<FriendRequest>> incomingRequestsAsync(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(snapshot.requests().stream()
                    .filter(request -> request.targetId().equals(targetId))
                    .toList());
        }
    }

    @Override
    public CompletionStage<List<FriendRequest>> outgoingRequestsAsync(UUID requesterId) {
        Objects.requireNonNull(requesterId, "requesterId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(snapshot.requests().stream()
                    .filter(request -> request.requesterId().equals(requesterId))
                    .toList());
        }
    }

    @Override
    public CompletionStage<Boolean> areFriendsAsync(UUID firstPlayerId, UUID secondPlayerId) {
        requireDifferentPlayers(firstPlayerId, secondPlayerId);
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(hasFriendship(snapshot, firstPlayerId, secondPlayerId));
        }
    }

    @Override
    public CompletionStage<Void> blockAsync(UUID blockerId, UUID blockedId) {
        requireDifferentPlayers(blockerId, blockedId);
        return enqueue(() -> {
            FriendSnapshot current;
            synchronized (stateLock) {
                current = snapshot;
                if (hasBlock(current, blockerId, blockedId)) {
                    return completedVoid();
                }
            }
            var block = new FriendBlock(blockerId, blockedId, clock.instant());
            var blocks = new ArrayList<>(current.blocks());
            blocks.add(block);
            var friendships = withoutFriendship(current.friendships(), blockerId, blockedId);
            var requests = withoutRequestsBetween(current.requests(), blockerId, blockedId);
            var events = new ArrayList<FriendEvent>();
            findFriendship(current, blockerId, blockedId)
                    .map(friendship -> new FriendshipRemovedEvent(friendship, blockerId))
                    .ifPresent(events::add);
            events.add(new FriendBlockedEvent(block));
            var next = nextSnapshot(current, friendships, requests, blocks);
            return commit(next, events);
        });
    }

    @Override
    public CompletionStage<Void> unblockAsync(UUID blockerId, UUID blockedId) {
        requireDifferentPlayers(blockerId, blockedId);
        return enqueue(() -> {
            FriendSnapshot current;
            FriendBlock block;
            synchronized (stateLock) {
                current = snapshot;
                var existing = findBlock(current, blockerId, blockedId);
                if (existing.isEmpty()) {
                    return completedVoid();
                }
                block = existing.orElseThrow();
            }
            var blocks = current.blocks().stream()
                    .filter(value -> !value.equals(block))
                    .toList();
            var next = nextSnapshot(current, current.friendships(), current.requests(), blocks);
            return commit(next, List.of(new FriendUnblockedEvent(block)));
        });
    }

    @Override
    public CompletionStage<List<FriendBlock>> blocksAsync(UUID blockerId) {
        Objects.requireNonNull(blockerId, "blockerId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(snapshot.blocks().stream()
                    .filter(block -> block.blockerId().equals(blockerId))
                    .toList());
        }
    }

    @Override
    public CompletionStage<Void> removeFriendAsync(UUID playerId, UUID friendId) {
        requireDifferentPlayers(playerId, friendId);
        return enqueue(() -> {
            FriendSnapshot current;
            Friendship friendship;
            synchronized (stateLock) {
                current = snapshot;
                friendship = findFriendship(current, playerId, friendId)
                        .orElseThrow(() -> new FriendshipNotFoundException(playerId, friendId));
            }
            var friendships = withoutFriendship(current.friendships(), playerId, friendId);
            var next = nextSnapshot(current, friendships, current.requests(), current.blocks());
            return commit(next, List.of(new FriendshipRemovedEvent(friendship, playerId)));
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
                    snapshot = FriendSnapshot.empty();
                }
            });
            return closeStage;
        }
    }

    private CompletionStage<Void> transitionRequest(UUID requesterId, UUID targetId, FriendEvent event) {
        FriendSnapshot current;
        synchronized (stateLock) {
            current = snapshot;
            requireRequest(current, requesterId, targetId);
        }
        var requests = current.requests().stream()
                .filter(request -> !request.requesterId().equals(requesterId)
                        || !request.targetId().equals(targetId))
                .toList();
        var next = nextSnapshot(current, current.friendships(), requests, current.blocks());
        return commit(next, List.of(event));
    }

    private CompletionStage<Void> commit(FriendSnapshot next, List<FriendEvent> events) {
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

    private CompletionStage<Void> persist(FriendSnapshot next) {
        if (repository == null) {
            return completedVoid();
        }
        return options.withRepositoryTimeout(
                Objects.requireNonNull(repository.saveAsync(next, next.revision() - 1), "repository save stage"));
    }

    private CompletionStage<Void> publish(FriendEvent event) {
        if (eventBus == null) {
            return completedVoid();
        }
        try {
            return options.withEventTimeout(Objects.requireNonNull(eventBus.publishAsync(event), "event stage"))
                    .handle((ignored, failure) -> {
                        if (failure != null) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Friend event publication failed: "
                                            + event.getClass().getName(),
                                    failure);
                        }
                        return null;
                    });
        } catch (RuntimeException failure) {
            LOGGER.log(
                    Level.WARNING,
                    "Friend event publication failed: " + event.getClass().getName(),
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

    private static FriendSnapshot nextSnapshot(
            FriendSnapshot current,
            List<Friendship> friendships,
            List<FriendRequest> requests,
            List<FriendBlock> blocks) {
        return new FriendSnapshot(current.revision() + 1, friendships, requests, blocks);
    }

    private static void requireDifferentPlayers(UUID firstPlayerId, UUID secondPlayerId) {
        Objects.requireNonNull(firstPlayerId, "firstPlayerId");
        Objects.requireNonNull(secondPlayerId, "secondPlayerId");
        if (firstPlayerId.equals(secondPlayerId)) {
            throw new IllegalArgumentException("player identifiers must differ");
        }
    }

    private static void requireNotBlocked(FriendSnapshot snapshot, UUID firstPlayerId, UUID secondPlayerId) {
        if (hasBlock(snapshot, firstPlayerId, secondPlayerId) || hasBlock(snapshot, secondPlayerId, firstPlayerId)) {
            throw new FriendBlockedException(firstPlayerId, secondPlayerId);
        }
    }

    private static void requireRequest(FriendSnapshot snapshot, UUID requesterId, UUID targetId) {
        if (!hasRequest(snapshot, requesterId, targetId)) {
            throw new FriendRequestException("friend request not found");
        }
    }

    private static boolean hasFriendship(FriendSnapshot snapshot, UUID firstPlayerId, UUID secondPlayerId) {
        return findFriendship(snapshot, firstPlayerId, secondPlayerId).isPresent();
    }

    private static Optional<Friendship> findFriendship(
            FriendSnapshot snapshot, UUID firstPlayerId, UUID secondPlayerId) {
        return snapshot.friendships().stream()
                .filter(friendship -> friendship.contains(firstPlayerId) && friendship.contains(secondPlayerId))
                .findFirst();
    }

    private static boolean hasRequest(FriendSnapshot snapshot, UUID requesterId, UUID targetId) {
        return snapshot.requests().stream()
                .anyMatch(request -> request.requesterId().equals(requesterId)
                        && request.targetId().equals(targetId));
    }

    private static boolean hasBlock(FriendSnapshot snapshot, UUID blockerId, UUID blockedId) {
        return findBlock(snapshot, blockerId, blockedId).isPresent();
    }

    private static Optional<FriendBlock> findBlock(FriendSnapshot snapshot, UUID blockerId, UUID blockedId) {
        return snapshot.blocks().stream()
                .filter(block ->
                        block.blockerId().equals(blockerId) && block.blockedId().equals(blockedId))
                .findFirst();
    }

    private static List<Friendship> withoutFriendship(
            List<Friendship> friendships, UUID firstPlayerId, UUID secondPlayerId) {
        return friendships.stream()
                .filter(friendship -> !(friendship.contains(firstPlayerId) && friendship.contains(secondPlayerId)))
                .toList();
    }

    private static List<FriendRequest> withoutRequestsBetween(
            List<FriendRequest> requests, UUID firstPlayerId, UUID secondPlayerId) {
        return requests.stream()
                .filter(request -> !isRequestBetween(request, firstPlayerId, secondPlayerId))
                .toList();
    }

    private static boolean isRequestBetween(FriendRequest request, UUID firstPlayerId, UUID secondPlayerId) {
        return (request.requesterId().equals(firstPlayerId)
                        && request.targetId().equals(secondPlayerId))
                || (request.requesterId().equals(secondPlayerId)
                        && request.targetId().equals(firstPlayerId));
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
        return new IllegalStateException("Friend service is closed");
    }
}

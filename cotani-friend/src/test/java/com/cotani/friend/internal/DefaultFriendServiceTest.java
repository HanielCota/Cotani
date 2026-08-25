package com.cotani.friend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import com.cotani.event.subscription.EventSubscription;
import com.cotani.friend.CotaniFriends;
import com.cotani.friend.api.FriendBlock;
import com.cotani.friend.api.FriendRepository;
import com.cotani.friend.api.FriendRequest;
import com.cotani.friend.api.FriendService;
import com.cotani.friend.api.FriendServiceOptions;
import com.cotani.friend.api.FriendSnapshot;
import com.cotani.friend.api.Friendship;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DefaultFriendServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void sendsAcceptsAndListsFriendships() {
        var service = service(null);

        var request = join(service.sendRequestAsync(ALICE, BOB));
        var friendship = join(service.acceptRequestAsync(BOB, request.requesterId()));

        assertTrue(friendship.contains(ALICE));
        assertEquals(List.of(friendship), join(service.friendsAsync(ALICE)));
        assertTrue(join(service.areFriendsAsync(ALICE, BOB)));
        assertTrue(join(service.incomingRequestsAsync(BOB)).isEmpty());
    }

    @Test
    void blockRemovesPendingRequestAndPreventsNewRequests() {
        var service = service(null);
        join(service.sendRequestAsync(ALICE, BOB));

        join(service.blockAsync(BOB, ALICE));

        assertTrue(join(service.incomingRequestsAsync(BOB)).isEmpty());
        assertThrows(CompletionException.class, () -> join(service.sendRequestAsync(ALICE, BOB)));
    }

    @Test
    void blockRemovesFriendshipAndUnblockAllowsNewRequest() {
        var service = service(null);
        var request = join(service.sendRequestAsync(ALICE, BOB));
        join(service.acceptRequestAsync(BOB, request.requesterId()));

        join(service.blockAsync(ALICE, BOB));
        assertFalse(join(service.areFriendsAsync(ALICE, BOB)));
        assertEquals(1, join(service.blocksAsync(ALICE)).size());

        join(service.unblockAsync(ALICE, BOB));
        join(service.sendRequestAsync(ALICE, BOB));
        assertEquals(0, join(service.blocksAsync(ALICE)).size());
    }

    @Test
    void declineAndCancelRemoveOnlyTheSelectedRequest() {
        var service = service(null);
        join(service.sendRequestAsync(ALICE, BOB));
        join(service.sendRequestAsync(ALICE, CAROL));

        join(service.declineRequestAsync(BOB, ALICE));
        join(service.cancelRequestAsync(ALICE, CAROL));

        assertTrue(join(service.incomingRequestsAsync(BOB)).isEmpty());
        assertTrue(join(service.outgoingRequestsAsync(ALICE)).isEmpty());
    }

    @Test
    void repositoryReceivesExpectedRevisionsBeforeVisibleStateChanges() {
        var repository = new RecordingRepository();
        var service = join(CotaniFriends.fromRepositoryAsync(repository));

        join(service.sendRequestAsync(ALICE, BOB));
        join(service.sendRequestAsync(ALICE, CAROL));

        assertEquals(List.of(0L, 1L), repository.expectedRevisions);
        assertEquals(2, repository.snapshot.revision());
    }

    @Test
    void eventFailureDoesNotUndoCommittedRequest() {
        var service = new DefaultFriendService(
                FriendSnapshot.empty(),
                null,
                new FailingEventBus(),
                FriendServiceOptions.defaults(),
                Clock.systemUTC());

        var request = join(service.sendRequestAsync(ALICE, BOB));

        assertEquals(List.of(request), join(service.outgoingRequestsAsync(ALICE)));
    }

    @Test
    void repositoryFailureDoesNotExposeUncommittedRequest() {
        var service = join(CotaniFriends.fromRepositoryAsync(new FailingRepository()));

        assertThrows(CompletionException.class, () -> join(service.sendRequestAsync(ALICE, BOB)));
        assertTrue(join(service.outgoingRequestsAsync(ALICE)).isEmpty());
    }

    @Test
    void snapshotsRejectDuplicateOrBlockedRelations() {
        var friendship = Friendship.create(ALICE, BOB, NOW);
        var request = new FriendRequest(ALICE, BOB, NOW);
        var block = new FriendBlock(ALICE, BOB, NOW);

        assertThrows(
                IllegalArgumentException.class,
                () -> new FriendSnapshot(List.of(friendship, friendship), List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new FriendSnapshot(List.of(), List.of(request), List.of(block)));
    }

    @Test
    void closeWaitsForOperationsAcceptedBeforeClose() {
        var repository = new BlockingRepository();
        var service = join(CotaniFriends.fromRepositoryAsync(repository));

        var first = service.sendRequestAsync(ALICE, BOB);
        var second = service.sendRequestAsync(ALICE, CAROL);
        var close = service.closeAsync();
        repository.completeFirstSave();

        join(first);
        join(second);
        join(close);
    }

    private static FriendService service(@Nullable EventBus eventBus) {
        return new DefaultFriendService(
                FriendSnapshot.empty(), null, eventBus, FriendServiceOptions.defaults(), new FixedClock(NOW));
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static class RecordingRepository implements FriendRepository {
        private FriendSnapshot snapshot = FriendSnapshot.empty();
        private final List<Long> expectedRevisions = new ArrayList<>();

        @Override
        public CompletionStage<FriendSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(snapshot);
        }

        @Override
        public CompletionStage<Void> saveAsync(FriendSnapshot next, long expectedRevision) {
            expectedRevisions.add(expectedRevision);
            if (snapshot.revision() != expectedRevision) {
                return CompletableFuture.failedFuture(new IllegalStateException("unexpected revision"));
            }
            snapshot = next;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class BlockingRepository extends RecordingRepository {
        private final CompletableFuture<Void> firstSave = new CompletableFuture<>();
        private boolean first = true;

        @Override
        public CompletionStage<Void> saveAsync(FriendSnapshot next, long expectedRevision) {
            if (!first) {
                return super.saveAsync(next, expectedRevision);
            }
            first = false;
            return firstSave.thenCompose(ignored -> super.saveAsync(next, expectedRevision));
        }

        private void completeFirstSave() {
            firstSave.complete(null);
        }
    }

    private static final class FailingRepository implements FriendRepository {
        @Override
        public CompletionStage<FriendSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(FriendSnapshot.empty());
        }

        @Override
        public CompletionStage<Void> saveAsync(FriendSnapshot snapshot, long expectedRevision) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"));
        }
    }

    private static class FailingEventBus implements EventBus {
        @Override
        public <T extends CotaniEvent> T publish(T event) {
            return event;
        }

        @Override
        public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
            return CompletableFuture.failedFuture(new IllegalStateException("event bus unavailable"));
        }

        @Override
        public <T extends CotaniEvent> EventSubscription subscribe(
                Class<T> eventType, EventListener<? super T> listener) {
            return new NoopSubscription();
        }

        @Override
        public <T extends CotaniEvent> EventSubscription subscribe(
                Class<T> eventType, EventPriority priority, EventListener<? super T> listener) {
            return new NoopSubscription();
        }

        @Override
        public <T extends CotaniEvent> EventSubscription subscribe(
                Class<T> eventType,
                EventPriority priority,
                boolean ignoreCancelled,
                EventListener<? super T> listener) {
            return new NoopSubscription();
        }

        @Override
        public void unsubscribe(EventSubscription subscription) {}

        @Override
        public void clear() {}
    }

    private static final class NoopSubscription implements EventSubscription {
        @Override
        public UUID id() {
            return UUID.randomUUID();
        }

        @Override
        public Class<? extends CotaniEvent> eventType() {
            return CotaniEvent.class;
        }

        @Override
        public EventPriority priority() {
            return EventPriority.NORMAL;
        }

        @Override
        public EventListener<? extends CotaniEvent> listener() {
            return ignored -> {};
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void unsubscribe() {}
    }

    private static final class FixedClock extends Clock {
        private final Instant instant;

        private FixedClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

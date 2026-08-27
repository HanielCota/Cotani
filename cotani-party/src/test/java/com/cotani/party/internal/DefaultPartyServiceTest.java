package com.cotani.party.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import com.cotani.event.subscription.EventSubscription;
import com.cotani.party.CotaniParties;
import com.cotani.party.api.Party;
import com.cotani.party.api.PartyId;
import com.cotani.party.api.PartyOptions;
import com.cotani.party.api.PartyRepository;
import com.cotani.party.api.PartyRole;
import com.cotani.party.api.PartyService;
import com.cotani.party.api.PartyServiceOptions;
import com.cotani.party.api.PartySnapshot;
import com.cotani.party.api.event.PartyLeadershipTransferredEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class DefaultPartyServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void createsInvitesAcceptsAndTransfersLeadership() {
        var service = service(new MutableClock(NOW));

        var created = join(service.createAsync(LEADER, new PartyOptions(3)));
        var invite = join(service.inviteAsync(created.id(), LEADER, MEMBER, Duration.ofMinutes(1)));
        var joined = join(service.acceptInviteAsync(MEMBER, invite.partyId()));

        assertEquals(2, joined.members().size());
        assertEquals(LEADER, joined.leaderId());

        var transferred = join(service.transferLeadershipAsync(joined.id(), LEADER, MEMBER));

        assertEquals(MEMBER, transferred.leaderId());
        assertEquals(PartyRole.OFFICER, transferred.member(LEADER).orElseThrow().role());
    }

    @Test
    void acceptingOneInvitationKeepsOtherInvitationsForTheSameParty() {
        var service = service(new MutableClock(NOW));
        var party = join(service.createAsync(LEADER, new PartyOptions(3)));
        join(service.inviteAsync(party.id(), LEADER, MEMBER, Duration.ofMinutes(1)));
        join(service.inviteAsync(party.id(), LEADER, OTHER_MEMBER, Duration.ofMinutes(1)));

        join(service.acceptInviteAsync(MEMBER, party.id()));
        var joined = join(service.acceptInviteAsync(OTHER_MEMBER, party.id()));

        assertEquals(3, joined.members().size());
        assertTrue(joined.contains(MEMBER));
        assertTrue(joined.contains(OTHER_MEMBER));
    }

    @Test
    void leaderLeavingSelectsTheOldestRemainingMember() {
        var clock = new MutableClock(NOW);
        var service = service(clock);
        var party = join(service.createAsync(LEADER, new PartyOptions(4)));

        var firstInvite = join(service.inviteAsync(party.id(), LEADER, MEMBER, Duration.ofMinutes(1)));
        join(service.acceptInviteAsync(MEMBER, firstInvite.partyId()));
        clock.advance(Duration.ofSeconds(1));
        var secondInvite = join(service.inviteAsync(party.id(), LEADER, OTHER_MEMBER, Duration.ofMinutes(1)));
        join(service.acceptInviteAsync(OTHER_MEMBER, secondInvite.partyId()));

        var remaining = join(service.leaveAsync(LEADER)).orElseThrow();

        assertEquals(MEMBER, remaining.leaderId());
        assertFalse(remaining.contains(LEADER));
        assertEquals(3, remaining.revision());
    }

    @Test
    void leaderLeavingPublishesTheImplicitLeadershipTransfer() {
        var eventBus = new RecordingEventBus();
        var service = new DefaultPartyService(
                List.of(), null, eventBus, PartyServiceOptions.defaults(), new MutableClock(NOW));
        var party = join(service.createAsync(LEADER, new PartyOptions(3)));
        var invite = join(service.inviteAsync(party.id(), LEADER, MEMBER, Duration.ofMinutes(1)));
        join(service.acceptInviteAsync(MEMBER, invite.partyId()));
        eventBus.events.clear();

        join(service.leaveAsync(LEADER));

        assertTrue(eventBus.events.stream().anyMatch(PartyLeadershipTransferredEvent.class::isInstance));
    }

    @Test
    void expiredInvitationsAreRemovedAndCannotBeAccepted() {
        var clock = new MutableClock(NOW);
        var service = service(clock);
        var party = join(service.createAsync(LEADER, PartyOptions.defaults()));
        join(service.inviteAsync(party.id(), LEADER, MEMBER, Duration.ofSeconds(1)));

        clock.advance(Duration.ofSeconds(1));

        assertTrue(join(service.invitesAsync(MEMBER)).isEmpty());
        assertThrows(CompletionException.class, () -> join(service.acceptInviteAsync(MEMBER, party.id())));
    }

    @Test
    void rejectsMultipleMembershipsAndUnauthorizedMutations() {
        var service = service(new MutableClock(NOW));
        var party = join(service.createAsync(LEADER, PartyOptions.defaults()));
        var invite = join(service.inviteAsync(party.id(), LEADER, MEMBER, Duration.ofMinutes(1)));
        join(service.acceptInviteAsync(MEMBER, invite.partyId()));

        assertThrows(CompletionException.class, () -> join(service.createAsync(MEMBER, PartyOptions.defaults())));
        assertThrows(
                CompletionException.class,
                () -> join(service.setRoleAsync(party.id(), MEMBER, LEADER, PartyRole.OFFICER)));
        assertThrows(CompletionException.class, () -> join(service.kickAsync(party.id(), MEMBER, LEADER)));
    }

    @Test
    void persistsMutationsBeforePublishingThemToTheLocalState() {
        var repository = new RecordingRepository();
        var service = join(CotaniParties.fromRepositoryAsync(repository, null, PartyServiceOptions.defaults()));

        var party = join(service.createAsync(LEADER, PartyOptions.defaults()));

        assertEquals(List.of(party), repository.saved);
        assertEquals(Optional.of(party), join(service.findAsync(party.id())));
    }

    @Test
    void eventFailureDoesNotUndoACommittedMutation() {
        var service = new DefaultPartyService(
                List.of(), null, new FailingEventBus(), PartyServiceOptions.defaults(), new MutableClock(NOW));

        var party = join(service.createAsync(LEADER, PartyOptions.defaults()));

        assertEquals(Optional.of(party), join(service.findAsync(party.id())));
    }

    @Test
    void noOpRoleAndLeadershipChangesDoNotPersistOrPublish() {
        var repository = new RecordingRepository();
        var eventBus = new RecordingEventBus();
        var service = join(CotaniParties.fromRepositoryAsync(repository, eventBus, PartyServiceOptions.defaults()));
        var party = join(service.createAsync(LEADER, new PartyOptions(3)));
        var invite = join(service.inviteAsync(party.id(), LEADER, MEMBER, Duration.ofMinutes(1)));
        var joined = join(service.acceptInviteAsync(MEMBER, invite.partyId()));
        var savedCount = repository.saved.size();
        eventBus.events.clear();

        assertEquals(List.of(0L), repository.expectedRevisions);
        assertEquals(joined, join(service.setRoleAsync(joined.id(), LEADER, MEMBER, PartyRole.MEMBER)));
        assertEquals(joined, join(service.transferLeadershipAsync(joined.id(), LEADER, LEADER)));
        assertEquals(savedCount, repository.saved.size());
        assertTrue(eventBus.events.isEmpty());
    }

    @Test
    void closeWaitsForOperationsAcceptedBeforeClose() {
        var repository = new BlockingCreateRepository();
        var service = join(CotaniParties.fromRepositoryAsync(repository, null, PartyServiceOptions.defaults()));

        var first = service.createAsync(LEADER, PartyOptions.defaults());
        var second = service.createAsync(MEMBER, PartyOptions.defaults());
        var close = service.closeAsync();

        repository.completeFirstCreate();

        assertEquals(LEADER, join(first).leaderId());
        assertEquals(MEMBER, join(second).leaderId());
        join(close);
    }

    @Test
    void rejectsOperationsAfterClose() {
        var service = service(new MutableClock(NOW));
        join(service.closeAsync());

        assertThrows(CompletionException.class, () -> join(service.createAsync(LEADER, PartyOptions.defaults())));
    }

    private static PartyService service(Clock clock) {
        return new DefaultPartyService(List.of(), null, null, PartyServiceOptions.defaults(), clock);
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static class RecordingRepository implements PartyRepository {
        private final List<Party> saved = new ArrayList<>();
        private final List<Long> expectedRevisions = new ArrayList<>();

        @Override
        public CompletionStage<PartySnapshot> loadAsync() {
            return CompletableFuture.completedFuture(new PartySnapshot(saved));
        }

        @Override
        public CompletionStage<Void> createAsync(Party party) {
            saved.removeIf(current -> current.id().equals(party.id()));
            saved.add(party);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> updateAsync(PartyId partyId, long expectedRevision, Party party) {
            expectedRevisions.add(expectedRevision);
            saved.removeIf(current -> current.id().equals(partyId));
            saved.add(party);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteAsync(PartyId partyId, long expectedRevision) {
            expectedRevisions.add(expectedRevision);
            saved.removeIf(current -> current.id().equals(partyId));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class BlockingCreateRepository extends RecordingRepository {
        private final CompletableFuture<Void> firstCreate = new CompletableFuture<>();
        private boolean first = true;

        @Override
        public CompletionStage<Void> createAsync(Party party) {
            if (!first) {
                return super.createAsync(party);
            }
            first = false;
            return firstCreate.thenCompose(ignored -> super.createAsync(party));
        }

        private void completeFirstCreate() {
            firstCreate.complete(null);
        }
    }

    private static class RecordingEventBus implements EventBus {
        private final List<CotaniEvent> events = new ArrayList<>();

        @Override
        public <T extends CotaniEvent> T publish(T event) {
            events.add(event);
            return event;
        }

        @Override
        public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
            events.add(event);
            return CompletableFuture.completedFuture(event);
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
        public void clear() {
            events.clear();
        }
    }

    private static final class FailingEventBus extends RecordingEventBus {
        @Override
        public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
            return CompletableFuture.failedFuture(new IllegalStateException("event bus unavailable"));
        }
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

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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

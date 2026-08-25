package com.cotani.party.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.party.api.Party;
import com.cotani.party.api.PartyAccessDeniedException;
import com.cotani.party.api.PartyConflictException;
import com.cotani.party.api.PartyException;
import com.cotani.party.api.PartyId;
import com.cotani.party.api.PartyInvite;
import com.cotani.party.api.PartyInviteException;
import com.cotani.party.api.PartyMembershipException;
import com.cotani.party.api.PartyNotFoundException;
import com.cotani.party.api.PartyOptions;
import com.cotani.party.api.PartyRepository;
import com.cotani.party.api.PartyRole;
import com.cotani.party.api.PartyService;
import com.cotani.party.api.PartyServiceOptions;
import com.cotani.party.api.event.PartyDisbandedEvent;
import com.cotani.party.api.event.PartyEvent;
import com.cotani.party.api.event.PartyLeadershipTransferredEvent;
import com.cotani.party.api.event.PartyMemberInvitedEvent;
import com.cotani.party.api.event.PartyMemberJoinedEvent;
import com.cotani.party.api.event.PartyMemberKickedEvent;
import com.cotani.party.api.event.PartyMemberLeftEvent;
import com.cotani.party.api.event.PartyRoleChangedEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public final class DefaultPartyService implements PartyService {
    private static final Logger LOGGER = Logger.getLogger(DefaultPartyService.class.getName());
    private final Object stateLock = new Object();
    private final Map<PartyId, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, PartyId> membership = new LinkedHashMap<>();
    private final Map<UUID, Map<PartyId, PartyInvite>> invites = new LinkedHashMap<>();
    private final @Nullable PartyRepository repository;
    private final @Nullable EventBus eventBus;
    private final PartyServiceOptions options;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();

    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    public DefaultPartyService(
            List<Party> initialParties,
            @Nullable PartyRepository repository,
            @Nullable EventBus eventBus,
            PartyServiceOptions options,
            Clock clock) {
        Objects.requireNonNull(initialParties, "initialParties");
        this.repository = repository;
        this.eventBus = eventBus;
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
        initialParties.forEach(this::registerInitialParty);
    }

    @Override
    public CompletionStage<Party> createAsync(UUID leaderId, PartyOptions partyOptions) {
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(partyOptions, "partyOptions");

        return enqueue(() -> {
            synchronized (stateLock) {
                if (membership.containsKey(leaderId)) {
                    throw new PartyMembershipException(leaderId, "player already belongs to a party");
                }
            }
            var party = Party.create(PartyId.random(), leaderId, partyOptions, clock.instant());
            return persistNew(party).thenCompose(ignored -> {
                synchronized (stateLock) {
                    registerPartyLocked(party);
                }
                return publish(new com.cotani.party.api.event.PartyCreatedEvent(party))
                        .thenApply(ignoredEvent -> party);
            });
        });
    }

    @Override
    public CompletionStage<Optional<Party>> findAsync(PartyId partyId) {
        Objects.requireNonNull(partyId, "partyId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            return completed(Optional.ofNullable(parties.get(partyId)));
        }
    }

    @Override
    public CompletionStage<Optional<Party>> findByMemberAsync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var partyId = membership.get(playerId);
            if (partyId == null) {
                return completed(Optional.empty());
            }
            return completed(Optional.ofNullable(parties.get(partyId)));
        }
    }

    @Override
    public CompletionStage<PartyInvite> inviteAsync(
            PartyId partyId, UUID inviterId, UUID inviteeId, Duration lifetime) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(inviterId, "inviterId");
        Objects.requireNonNull(inviteeId, "inviteeId");
        Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }

        return enqueue(() -> {
            PartyInvite invite;
            synchronized (stateLock) {
                var party = requirePartyLocked(partyId);
                requirePartyManager(party, inviterId);
                requireNotMemberLocked(inviteeId);
                cleanupInvitesLocked(inviteeId, clock.instant());
                var playerInvites = invites.computeIfAbsent(inviteeId, ignored -> new LinkedHashMap<>());
                if (playerInvites.containsKey(partyId)) {
                    throw new PartyInviteException(inviteeId, partyId, "an active invitation already exists");
                }
                invite = new PartyInvite(
                        partyId, inviterId, inviteeId, clock.instant().plus(lifetime));
                playerInvites.put(partyId, invite);
            }
            return publish(new PartyMemberInvitedEvent(partyId, inviterId, inviteeId, invite.expiresAt()))
                    .thenApply(ignoredEvent -> invite);
        });
    }

    @Override
    public CompletionStage<Party> acceptInviteAsync(UUID inviteeId, PartyId partyId) {
        Objects.requireNonNull(inviteeId, "inviteeId");
        Objects.requireNonNull(partyId, "partyId");

        return enqueue(() -> {
            Party party;
            PartyInvite invite;
            synchronized (stateLock) {
                requireNotMemberLocked(inviteeId);
                party = requirePartyLocked(partyId);
                invite = findInviteLocked(inviteeId, partyId);
                if (invite.isExpiredAt(clock.instant())) {
                    removeInviteLocked(inviteeId, partyId);
                    throw new PartyInviteException(inviteeId, partyId, "invitation has expired");
                }
                if (party.isFull()) {
                    throw new PartyMembershipException(inviteeId, "party is full");
                }
            }
            var updated = party.addMember(inviteeId, clock.instant());
            return persistUpdate(party, updated).thenCompose(ignored -> {
                synchronized (stateLock) {
                    replacePartyLocked(updated);
                    removeInviteLocked(inviteeId, partyId);
                }
                return publish(new PartyMemberJoinedEvent(updated, inviteeId)).thenApply(ignoredEvent -> updated);
            });
        });
    }

    @Override
    public CompletionStage<Void> declineInviteAsync(UUID inviteeId, PartyId partyId) {
        Objects.requireNonNull(inviteeId, "inviteeId");
        Objects.requireNonNull(partyId, "partyId");
        return enqueue(() -> {
            synchronized (stateLock) {
                findInviteLocked(inviteeId, partyId);
                removeInviteLocked(inviteeId, partyId);
            }
            return completedVoid();
        });
    }

    @Override
    public CompletionStage<List<PartyInvite>> invitesAsync(UUID inviteeId) {
        Objects.requireNonNull(inviteeId, "inviteeId");
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            cleanupInvitesLocked(inviteeId, clock.instant());
            var playerInvites = invites.get(inviteeId);
            if (playerInvites == null) {
                return completed(List.of());
            }
            return completed(List.copyOf(playerInvites.values()));
        }
    }

    @Override
    public CompletionStage<Optional<Party>> leaveAsync(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        return enqueue(() -> {
            Party party;
            synchronized (stateLock) {
                party = requireMemberPartyLocked(memberId);
            }
            if (party.members().size() == 1) {
                return delete(party).thenCompose(ignored -> {
                    synchronized (stateLock) {
                        removePartyLocked(party.id());
                    }
                    return publish(new PartyMemberLeftEvent(party, memberId))
                            .thenCompose(ignoredEvent -> publish(new PartyDisbandedEvent(party.id(), memberId)))
                            .thenApply(ignoredEvent -> Optional.empty());
                });
            }

            if (!memberId.equals(party.leaderId())) {
                var updated = party.removeMember(memberId);
                return persistAndReplace(party, updated, List.of(new PartyMemberLeftEvent(updated, memberId)))
                        .thenApply(Optional::of);
            }

            var successor = party.members().stream()
                    .filter(member -> !member.playerId().equals(memberId))
                    .min(Comparator.comparing(com.cotani.party.api.PartyMember::joinedAt)
                            .thenComparing(member -> member.playerId().toString()))
                    .orElseThrow();
            var updated = party.removeLeaderAndTransfer(memberId, successor.playerId());
            return persistAndReplace(
                            party,
                            updated,
                            List.of(
                                    new PartyLeadershipTransferredEvent(updated, memberId, successor.playerId()),
                                    new PartyMemberLeftEvent(updated, memberId)))
                    .thenApply(Optional::of);
        });
    }

    @Override
    public CompletionStage<Party> kickAsync(PartyId partyId, UUID actorId, UUID memberId) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(memberId, "memberId");
        return enqueue(() -> {
            Party party;
            synchronized (stateLock) {
                party = requirePartyLocked(partyId);
                requirePartyManager(party, actorId);
                requireMember(party, memberId);
                if (actorId.equals(memberId)) {
                    throw new PartyMembershipException(memberId, "use leaveAsync to leave the party");
                }
                if (party.leaderId().equals(memberId)) {
                    throw new PartyAccessDeniedException(actorId, partyId);
                }
            }
            var updated = party.removeMember(memberId);
            return persistAndReplace(party, updated, List.of(new PartyMemberKickedEvent(updated, actorId, memberId)));
        });
    }

    @Override
    public CompletionStage<Party> setRoleAsync(PartyId partyId, UUID actorId, UUID memberId, PartyRole role) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(role, "role");
        if (role == PartyRole.LEADER) {
            throw new IllegalArgumentException("Use transferLeadershipAsync to assign the leader role");
        }

        return enqueue(() -> {
            Party party;
            synchronized (stateLock) {
                party = requirePartyLocked(partyId);
                if (!party.leaderId().equals(actorId)) {
                    throw new PartyAccessDeniedException(actorId, partyId);
                }
                requireMember(party, memberId);
                if (party.leaderId().equals(memberId)) {
                    throw new PartyAccessDeniedException(actorId, partyId);
                }
            }
            var updated = party.withMemberRole(memberId, role);
            if (updated.equals(party)) {
                return completed(party);
            }
            return persistAndReplace(
                    party, updated, List.of(new PartyRoleChangedEvent(updated, actorId, memberId, role)));
        });
    }

    @Override
    public CompletionStage<Party> transferLeadershipAsync(PartyId partyId, UUID actorId, UUID newLeaderId) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(newLeaderId, "newLeaderId");
        return enqueue(() -> {
            Party party;
            synchronized (stateLock) {
                party = requirePartyLocked(partyId);
                if (!party.leaderId().equals(actorId)) {
                    throw new PartyAccessDeniedException(actorId, partyId);
                }
                requireMember(party, newLeaderId);
            }
            var updated = party.transferLeadership(newLeaderId);
            if (updated.equals(party)) {
                return completed(party);
            }
            return persistAndReplace(
                    party, updated, List.of(new PartyLeadershipTransferredEvent(updated, actorId, newLeaderId)));
        });
    }

    @Override
    public CompletionStage<Void> disbandAsync(PartyId partyId, UUID actorId) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(actorId, "actorId");
        return enqueue(() -> {
            Party party;
            synchronized (stateLock) {
                party = requirePartyLocked(partyId);
                if (!party.leaderId().equals(actorId)) {
                    throw new PartyAccessDeniedException(actorId, partyId);
                }
            }
            return delete(party).thenCompose(ignored -> {
                synchronized (stateLock) {
                    removePartyLocked(partyId);
                }
                return publish(new PartyDisbandedEvent(partyId, actorId)).thenApply(ignoredEvent -> null);
            });
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
                    parties.clear();
                    membership.clear();
                    invites.clear();
                }
            });
            return closeStage;
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

    private CompletionStage<Void> persistNew(Party party) {
        if (repository == null) {
            return completedVoid();
        }
        return options.withRepositoryTimeout(
                Objects.requireNonNull(repository.createAsync(party), "repository create stage"));
    }

    private CompletionStage<Void> persistUpdate(Party previous, Party next) {
        if (repository == null) {
            return completedVoid();
        }
        return options.withRepositoryTimeout(Objects.requireNonNull(
                repository.updateAsync(next.id(), previous.revision(), next), "repository update stage"));
    }

    private CompletionStage<Void> delete(Party party) {
        if (repository == null) {
            return completedVoid();
        }
        return options.withRepositoryTimeout(Objects.requireNonNull(
                repository.deleteAsync(party.id(), party.revision()), "repository delete stage"));
    }

    private CompletionStage<Party> persistAndReplace(Party previous, Party next, List<PartyEvent> events) {
        return persistUpdate(previous, next).thenCompose(ignored -> {
            synchronized (stateLock) {
                replacePartyLocked(next);
            }
            CompletionStage<Void> publication = completedVoid();
            for (var event : events) {
                publication = publication.thenCompose(ignoredEvent -> publish(event));
            }
            return publication.thenApply(ignoredEvent -> next);
        });
    }

    private CompletionStage<Void> publish(PartyEvent event) {
        if (eventBus == null) {
            return completedVoid();
        }
        try {
            return options.withEventTimeout(Objects.requireNonNull(eventBus.publishAsync(event), "event stage"))
                    .handle((ignored, failure) -> {
                        if (failure != null) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Party event publication failed: "
                                            + event.getClass().getName(),
                                    failure);
                        }
                        return null;
                    });
        } catch (RuntimeException failure) {
            LOGGER.log(
                    Level.WARNING,
                    "Party event publication failed: " + event.getClass().getName(),
                    failure);
            return completedVoid();
        }
    }

    private Party requirePartyLocked(PartyId partyId) {
        var party = parties.get(partyId);
        if (party == null) {
            throw new PartyNotFoundException(partyId);
        }
        return party;
    }

    private Party requireMemberPartyLocked(UUID playerId) {
        var partyId = membership.get(playerId);
        if (partyId == null) {
            throw new PartyMembershipException(playerId, "player does not belong to a party");
        }
        return requirePartyLocked(partyId);
    }

    private void requireNotMemberLocked(UUID playerId) {
        if (membership.containsKey(playerId)) {
            throw new PartyMembershipException(playerId, "player already belongs to a party");
        }
    }

    private static void requireMember(Party party, UUID playerId) {
        if (!party.contains(playerId)) {
            throw new PartyMembershipException(playerId, "player is not a member of the party");
        }
    }

    private static void requirePartyManager(Party party, UUID playerId) {
        var member = party.member(playerId)
                .orElseThrow(() -> new PartyMembershipException(playerId, "player is not a member of the party"));
        if (member.role() != PartyRole.LEADER && member.role() != PartyRole.OFFICER) {
            throw new PartyAccessDeniedException(playerId, party.id());
        }
    }

    private PartyInvite findInviteLocked(UUID inviteeId, PartyId partyId) {
        cleanupInvitesLocked(inviteeId, clock.instant());
        var playerInvites = invites.get(inviteeId);
        if (playerInvites == null) {
            throw new PartyInviteException(inviteeId, partyId, "invitation not found");
        }
        var invite = playerInvites.get(partyId);
        if (invite == null) {
            throw new PartyInviteException(inviteeId, partyId, "invitation not found");
        }
        return invite;
    }

    private void cleanupInvitesLocked(UUID inviteeId, Instant now) {
        var playerInvites = invites.get(inviteeId);
        if (playerInvites == null) {
            return;
        }
        playerInvites.values().removeIf(invite -> invite.isExpiredAt(now));
        if (playerInvites.isEmpty()) {
            invites.remove(inviteeId);
        }
    }

    private void removeInviteLocked(UUID inviteeId, PartyId partyId) {
        var playerInvites = invites.get(inviteeId);
        if (playerInvites == null) {
            return;
        }
        playerInvites.remove(partyId);
        if (playerInvites.isEmpty()) {
            invites.remove(inviteeId);
        }
    }

    private void registerInitialParty(Party party) {
        synchronized (stateLock) {
            if (parties.containsKey(party.id())) {
                throw new PartyConflictException(party.id());
            }
            registerPartyLocked(party);
        }
    }

    private void registerPartyLocked(Party party) {
        if (parties.containsKey(party.id())) {
            throw new PartyConflictException(party.id());
        }
        party.members().forEach(member -> {
            var previous = membership.get(member.playerId());
            if (previous != null && !previous.equals(party.id())) {
                throw new PartyException("Player belongs to multiple parties: " + member.playerId());
            }
        });
        parties.put(party.id(), party);
        party.members().forEach(member -> membership.put(member.playerId(), party.id()));
    }

    private void replacePartyLocked(Party party) {
        removePartyLocked(party.id());
        registerPartyLocked(party);
    }

    private void removePartyLocked(PartyId partyId) {
        var previous = parties.remove(partyId);
        if (previous != null) {
            previous.members().forEach(member -> membership.remove(member.playerId(), partyId));
        }
        invites.values().forEach(playerInvites -> playerInvites.remove(partyId));
        invites.entrySet().removeIf(entry -> entry.getValue().isEmpty());
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
        return new IllegalStateException("Party service is closed");
    }
}

package com.cotani.party.api;

import com.cotani.AsyncCloseable;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous party use cases using immutable player identifiers. */
public interface PartyService extends AsyncCloseable {
    /** Creates a party with the supplied leader and size policy. */
    CompletionStage<Party> createAsync(UUID leaderId, PartyOptions options);

    /** Finds a party without waiting behind pending mutations. */
    CompletionStage<Optional<Party>> findAsync(PartyId partyId);

    /** Finds the party containing one player without waiting behind pending mutations. */
    CompletionStage<Optional<Party>> findByMemberAsync(UUID playerId);

    /** Creates an expiring invitation from a manager to a player. */
    CompletionStage<PartyInvite> inviteAsync(PartyId partyId, UUID inviterId, UUID inviteeId, Duration lifetime);

    /** Accepts an active invitation and adds the invitee to the party. */
    CompletionStage<Party> acceptInviteAsync(UUID inviteeId, PartyId partyId);

    /** Declines an active invitation. */
    CompletionStage<Void> declineInviteAsync(UUID inviteeId, PartyId partyId);

    /** Returns the active invitations for one player. */
    CompletionStage<List<PartyInvite>> invitesAsync(UUID inviteeId);

    /** Removes a member, selecting a deterministic successor when the leader leaves. */
    CompletionStage<Optional<Party>> leaveAsync(UUID memberId);

    /** Removes a non-leader member through a manager action. */
    CompletionStage<Party> kickAsync(PartyId partyId, UUID actorId, UUID memberId);

    /** Changes a non-leader member's role. */
    CompletionStage<Party> setRoleAsync(PartyId partyId, UUID actorId, UUID memberId, PartyRole role);

    /** Transfers leadership to an existing member. */
    CompletionStage<Party> transferLeadershipAsync(PartyId partyId, UUID actorId, UUID newLeaderId);

    /** Permanently disbands a party through its leader. */
    CompletionStage<Void> disbandAsync(PartyId partyId, UUID actorId);
}

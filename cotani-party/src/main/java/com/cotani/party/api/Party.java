package com.cotani.party.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable party aggregate. */
public record Party(
        PartyId id, int maxMembers, Instant createdAt, long revision, UUID leaderId, Set<PartyMember> members) {

    public Party {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(members, "members");
        if (maxMembers < 2) {
            throw new IllegalArgumentException("maxMembers must be at least 2");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }

        var copiedMembers = new LinkedHashSet<PartyMember>();
        members.forEach(member -> {
            var nonNullMember = Objects.requireNonNull(member, "member");
            if (!copiedMembers.add(nonNullMember)) {
                throw new IllegalArgumentException("Party contains duplicate member entries");
            }
        });
        if (copiedMembers.isEmpty()) {
            throw new IllegalArgumentException("Party must contain at least one member");
        }
        if (copiedMembers.size() > maxMembers) {
            throw new IllegalArgumentException("Party contains more members than maxMembers");
        }
        if (copiedMembers.stream()
                        .filter(member -> member.role() == PartyRole.LEADER)
                        .count()
                != 1) {
            throw new IllegalArgumentException("Party must contain exactly one leader");
        }
        var leader = member(leaderId, copiedMembers);
        if (leader.isEmpty() || leader.orElseThrow().role() != PartyRole.LEADER) {
            throw new IllegalArgumentException("leaderId must identify the party leader");
        }
        var memberIds = copiedMembers.stream().map(PartyMember::playerId).toList();
        if (memberIds.stream().distinct().count() != memberIds.size()) {
            throw new IllegalArgumentException("Party cannot contain the same player twice");
        }
        members = Collections.unmodifiableSet(copiedMembers);
    }

    public static Party create(PartyId id, UUID leaderId, PartyOptions options, Instant createdAt) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(createdAt, "createdAt");
        return new Party(
                id,
                options.maxMembers(),
                createdAt,
                0,
                leaderId,
                Set.of(new PartyMember(leaderId, PartyRole.LEADER, createdAt)));
    }

    public Optional<PartyMember> member(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return member(playerId, members);
    }

    public boolean contains(UUID playerId) {
        return member(playerId).isPresent();
    }

    public boolean isFull() {
        return members.size() >= maxMembers;
    }

    public Party addMember(UUID playerId, Instant joinedAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(joinedAt, "joinedAt");
        if (contains(playerId)) {
            throw new IllegalArgumentException("Player is already a party member");
        }
        if (isFull()) {
            throw new IllegalStateException("Party is full");
        }
        var updated = new LinkedHashSet<>(members);
        updated.add(new PartyMember(playerId, PartyRole.MEMBER, joinedAt));
        return nextRevision(updated, leaderId);
    }

    public Party removeMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (playerId.equals(leaderId)) {
            throw new IllegalArgumentException("Transfer leadership before removing the leader");
        }
        if (member(playerId).isEmpty()) {
            throw new IllegalArgumentException("Player is not a party member");
        }
        var updated = new LinkedHashSet<>(members);
        updated.removeIf(member -> member.playerId().equals(playerId));
        return nextRevision(updated, leaderId);
    }

    public Party removeLeaderAndTransfer(UUID departingLeaderId, UUID successorId) {
        Objects.requireNonNull(departingLeaderId, "departingLeaderId");
        Objects.requireNonNull(successorId, "successorId");
        if (!departingLeaderId.equals(leaderId)) {
            throw new IllegalArgumentException("departingLeaderId must be the current leader");
        }
        if (departingLeaderId.equals(successorId)) {
            throw new IllegalArgumentException("successorId must differ from departingLeaderId");
        }
        if (member(successorId).isEmpty()) {
            throw new IllegalArgumentException("Successor must already be a party member");
        }
        var updated = new LinkedHashSet<PartyMember>();
        members.forEach(member -> {
            if (member.playerId().equals(departingLeaderId)) {
                return;
            }
            if (member.playerId().equals(successorId)) {
                updated.add(new PartyMember(member.playerId(), PartyRole.LEADER, member.joinedAt()));
                return;
            }
            updated.add(member);
        });
        return nextRevision(updated, successorId);
    }

    public Party withMemberRole(UUID playerId, PartyRole role) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
        if (role == PartyRole.LEADER) {
            throw new IllegalArgumentException("Use transferLeadership to change the leader");
        }
        if (member(playerId).isEmpty()) {
            throw new IllegalArgumentException("Player is not a party member");
        }
        if (member(playerId).orElseThrow().role() == role) {
            return this;
        }
        var updated = new LinkedHashSet<PartyMember>();
        members.forEach(member -> {
            if (member.playerId().equals(playerId)) {
                updated.add(new PartyMember(member.playerId(), role, member.joinedAt()));
                return;
            }
            updated.add(member);
        });
        return nextRevision(updated, leaderId);
    }

    public Party transferLeadership(UUID newLeaderId) {
        Objects.requireNonNull(newLeaderId, "newLeaderId");
        if (member(newLeaderId).isEmpty()) {
            throw new IllegalArgumentException("New leader must already be a party member");
        }
        if (newLeaderId.equals(leaderId)) {
            return this;
        }
        var updated = new LinkedHashSet<PartyMember>();
        members.forEach(member -> {
            if (member.playerId().equals(leaderId)) {
                updated.add(new PartyMember(member.playerId(), PartyRole.OFFICER, member.joinedAt()));
                return;
            }
            if (member.playerId().equals(newLeaderId)) {
                updated.add(new PartyMember(member.playerId(), PartyRole.LEADER, member.joinedAt()));
                return;
            }
            updated.add(member);
        });
        return nextRevision(updated, newLeaderId);
    }

    private Party nextRevision(Set<PartyMember> updatedMembers, UUID updatedLeaderId) {
        return new Party(id, maxMembers, createdAt, revision + 1, updatedLeaderId, updatedMembers);
    }

    private static Optional<PartyMember> member(UUID playerId, Set<PartyMember> source) {
        return source.stream()
                .filter(member -> member.playerId().equals(playerId))
                .findFirst();
    }
}

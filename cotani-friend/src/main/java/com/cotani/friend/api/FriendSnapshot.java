package com.cotani.friend.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable repository state for the complete friendship domain. */
public record FriendSnapshot(
        long revision, List<Friendship> friendships, List<FriendRequest> requests, List<FriendBlock> blocks) {
    public FriendSnapshot {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        Objects.requireNonNull(friendships, "friendships");
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(blocks, "blocks");
        friendships.forEach(value -> Objects.requireNonNull(value, "friendship"));
        requests.forEach(value -> Objects.requireNonNull(value, "request"));
        blocks.forEach(value -> Objects.requireNonNull(value, "block"));
        friendships = List.copyOf(friendships);
        requests = List.copyOf(requests);
        blocks = List.copyOf(blocks);
        validateRelations(friendships, requests, blocks);
    }

    public FriendSnapshot(List<Friendship> friendships, List<FriendRequest> requests, List<FriendBlock> blocks) {
        this(0, friendships, requests, blocks);
    }

    public static FriendSnapshot empty() {
        return new FriendSnapshot(0, List.of(), List.of(), List.of());
    }

    private static void validateRelations(
            List<Friendship> friendships, List<FriendRequest> requests, List<FriendBlock> blocks) {
        var friendshipPairs = new HashSet<String>();
        friendships.forEach(friendship -> {
            if (!friendshipPairs.add(pairKey(friendship.firstPlayerId(), friendship.secondPlayerId()))) {
                throw new IllegalArgumentException("Snapshot contains duplicate friendships");
            }
        });

        var requestPairs = new HashSet<String>();
        requests.forEach(request -> {
            var pairKey = directedKey(request.requesterId(), request.targetId());
            var reverseKey = directedKey(request.targetId(), request.requesterId());
            if (!requestPairs.add(pairKey) || requestPairs.contains(reverseKey)) {
                throw new IllegalArgumentException("Snapshot contains conflicting friend requests");
            }
            if (friendshipPairs.contains(pairKey(request.requesterId(), request.targetId()))) {
                throw new IllegalArgumentException("Snapshot contains a request for an existing friendship");
            }
        });

        var blockPairs = new HashSet<String>();
        blocks.forEach(block -> {
            var pairKey = pairKey(block.blockerId(), block.blockedId());
            if (!blockPairs.add(directedKey(block.blockerId(), block.blockedId()))) {
                throw new IllegalArgumentException("Snapshot contains duplicate friend blocks");
            }
            if (friendshipPairs.contains(pairKey)
                    || requests.stream()
                            .anyMatch(request -> isBetween(
                                    request.requesterId(), request.targetId(), block.blockerId(), block.blockedId()))) {
                throw new IllegalArgumentException("Snapshot contains a blocked relationship");
            }
        });
    }

    private static String pairKey(UUID firstPlayerId, UUID secondPlayerId) {
        return firstPlayerId.compareTo(secondPlayerId) < 0
                ? directedKey(firstPlayerId, secondPlayerId)
                : directedKey(secondPlayerId, firstPlayerId);
    }

    private static String directedKey(UUID requesterId, UUID targetId) {
        return requesterId + ":" + targetId;
    }

    private static boolean isBetween(
            UUID firstPlayerId, UUID secondPlayerId, UUID expectedFirstId, UUID expectedSecondId) {
        return (firstPlayerId.equals(expectedFirstId) && secondPlayerId.equals(expectedSecondId))
                || (firstPlayerId.equals(expectedSecondId) && secondPlayerId.equals(expectedFirstId));
    }
}

package com.cotani.friend.api;

import com.cotani.AsyncCloseable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous friendship, request and block use cases using immutable player identifiers.
 *
 * <p>Mutations are serialized and become visible only after repository persistence succeeds.
 * Queries return the latest committed snapshot without waiting behind a pending mutation. After
 * {@link #closeAsync()} begins, new operations are rejected and already accepted operations are
 * allowed to finish.
 */
public interface FriendService extends AsyncCloseable {
    /** Sends a directed request unless the players are already connected or blocked. */
    CompletionStage<FriendRequest> sendRequestAsync(UUID requesterId, UUID targetId);

    /** Accepts a request addressed to {@code targetId} from {@code requesterId}. */
    CompletionStage<Friendship> acceptRequestAsync(UUID targetId, UUID requesterId);

    /** Declines a request addressed to {@code targetId} from {@code requesterId}. */
    CompletionStage<Void> declineRequestAsync(UUID targetId, UUID requesterId);

    /** Cancels a request sent by {@code requesterId} to {@code targetId}. */
    CompletionStage<Void> cancelRequestAsync(UUID requesterId, UUID targetId);

    /** Returns all friendships containing the supplied player. */
    CompletionStage<List<Friendship>> friendsAsync(UUID playerId);

    /** Returns requests received by the supplied player. */
    CompletionStage<List<FriendRequest>> incomingRequestsAsync(UUID targetId);

    /** Returns requests sent by the supplied player. */
    CompletionStage<List<FriendRequest>> outgoingRequestsAsync(UUID requesterId);

    /** Returns whether two players currently have a friendship. */
    CompletionStage<Boolean> areFriendsAsync(UUID firstPlayerId, UUID secondPlayerId);

    /** Blocks a player and removes any friendship or pending requests between both players. */
    CompletionStage<Void> blockAsync(UUID blockerId, UUID blockedId);

    /** Removes a block created by {@code blockerId}. */
    CompletionStage<Void> unblockAsync(UUID blockerId, UUID blockedId);

    /** Returns players blocked by the supplied player. */
    CompletionStage<List<FriendBlock>> blocksAsync(UUID blockerId);

    /** Removes an existing friendship. */
    CompletionStage<Void> removeFriendAsync(UUID playerId, UUID friendId);
}

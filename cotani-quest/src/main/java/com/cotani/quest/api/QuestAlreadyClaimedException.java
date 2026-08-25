package com.cotani.quest.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Raised when a completed quest is claimed with a different idempotency key. */
public final class QuestAlreadyClaimedException extends QuestException {
    private static final long serialVersionUID = 1L;
    private final transient UUID playerId;
    private final transient QuestId questId;
    private final transient @Nullable QuestClaimId existingClaimId;

    public QuestAlreadyClaimedException(UUID playerId, QuestId questId) {
        this(playerId, questId, null);
    }

    public QuestAlreadyClaimedException(UUID playerId, QuestId questId, @Nullable QuestClaimId existingClaimId) {
        super("Quest was already claimed for player "
                + Objects.requireNonNull(playerId, "playerId")
                + ": "
                + Objects.requireNonNull(questId, "questId").value());
        this.playerId = playerId;
        this.questId = questId;
        this.existingClaimId = existingClaimId;
    }

    public UUID playerId() {
        return playerId;
    }

    public QuestId questId() {
        return questId;
    }

    public Optional<QuestClaimId> existingClaimId() {
        return Optional.ofNullable(existingClaimId);
    }
}

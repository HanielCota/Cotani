package com.cotani.trade.api;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable two-player trade aggregate. */
public record TradeSession(
        TradeId id,
        UUID initiatorId,
        UUID recipientId,
        Instant createdAt,
        Instant expiresAt,
        long revision,
        TradeStatus status,
        TradeOffer initiatorOffer,
        TradeOffer recipientOffer,
        Set<UUID> confirmations) {
    public TradeSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(initiatorId, "initiatorId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(initiatorOffer, "initiatorOffer");
        Objects.requireNonNull(recipientOffer, "recipientOffer");
        Objects.requireNonNull(confirmations, "confirmations");
        if (initiatorId.equals(recipientId)) {
            throw new IllegalArgumentException("initiator and recipient must differ");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (!initiatorOffer.ownerId().equals(initiatorId)
                || !recipientOffer.ownerId().equals(recipientId)) {
            throw new IllegalArgumentException("offer owners must match trade participants");
        }
        var participants = Set.of(initiatorId, recipientId);
        var copiedConfirmations = Set.copyOf(confirmations);
        if (!participants.containsAll(copiedConfirmations)) {
            throw new IllegalArgumentException("confirmations must belong to trade participants");
        }
        if (status != TradeStatus.OPEN && !copiedConfirmations.isEmpty()) {
            throw new IllegalArgumentException("only open trades may contain confirmations");
        }
        confirmations = copiedConfirmations;
    }

    public static TradeSession create(
            TradeId id, UUID initiatorId, UUID recipientId, TradeOptions options, Instant now) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(now, "now");
        return new TradeSession(
                id,
                initiatorId,
                recipientId,
                now,
                now.plus(options.lifetime()),
                0,
                TradeStatus.OPEN,
                TradeOffer.empty(initiatorId),
                TradeOffer.empty(recipientId),
                Set.of());
    }

    public boolean contains(UUID playerId) {
        return initiatorId.equals(playerId) || recipientId.equals(playerId);
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(expiresAt);
    }

    public boolean isActiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (status == TradeStatus.SETTLEMENT_PENDING) {
            return true;
        }
        return status == TradeStatus.OPEN && !isExpiredAt(instant);
    }

    public boolean bothConfirmed() {
        return confirmations.size() == 2;
    }

    public TradeOffer offerOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (initiatorId.equals(playerId)) {
            return initiatorOffer;
        }
        if (recipientId.equals(playerId)) {
            return recipientOffer;
        }
        throw new IllegalArgumentException("player is not a trade participant");
    }

    public TradeSession withOffer(UUID playerId, List<TradeAsset> assets) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(assets, "assets");
        if (!contains(playerId)) {
            throw new IllegalArgumentException("player is not a trade participant");
        }
        requireOpen();
        var updatedOffer = new TradeOffer(playerId, assets);
        return new TradeSession(
                id,
                initiatorId,
                recipientId,
                createdAt,
                expiresAt,
                revision + 1,
                TradeStatus.OPEN,
                initiatorId.equals(playerId) ? updatedOffer : initiatorOffer,
                recipientId.equals(playerId) ? updatedOffer : recipientOffer,
                Set.of());
    }

    public TradeSession withConfirmation(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!contains(playerId)) {
            throw new IllegalArgumentException("player is not a trade participant");
        }
        requireOpen();
        if (confirmations.contains(playerId)) {
            return this;
        }
        var updated = new HashSet<>(confirmations);
        updated.add(playerId);
        return new TradeSession(
                id,
                initiatorId,
                recipientId,
                createdAt,
                expiresAt,
                revision + 1,
                TradeStatus.OPEN,
                initiatorOffer,
                recipientOffer,
                updated);
    }

    public TradeSession withStatus(TradeStatus nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        if (status == nextStatus) {
            return this;
        }
        if (!isAllowedTransition(status, nextStatus)) {
            throw new IllegalStateException("invalid trade transition: " + status + " -> " + nextStatus);
        }
        return new TradeSession(
                id,
                initiatorId,
                recipientId,
                createdAt,
                expiresAt,
                revision + 1,
                nextStatus,
                initiatorOffer,
                recipientOffer,
                Set.of());
    }

    private void requireOpen() {
        if (status != TradeStatus.OPEN) {
            throw new IllegalStateException("trade is not open: " + status);
        }
    }

    private static boolean isAllowedTransition(TradeStatus current, TradeStatus next) {
        return switch (current) {
            case OPEN ->
                next == TradeStatus.SETTLEMENT_PENDING || next == TradeStatus.CANCELLED || next == TradeStatus.EXPIRED;
            case SETTLEMENT_PENDING -> next == TradeStatus.COMPLETED || next == TradeStatus.FAILED;
            case COMPLETED, CANCELLED, EXPIRED, FAILED -> false;
        };
    }
}

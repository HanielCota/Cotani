package com.cotani.inventory.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

/**
 * Coordination lock for cross-server player inventory transfers.
 * Prevents race conditions and inventory duplication when players switch servers rapidly.
 */
@NullMarked
public interface CrossServerTransferLock {

    /**
     * Attempts to acquire an exclusive lock for the player across the server network.
     *
     * @param playerId player unique identifier
     * @param duration lock lease duration before automatic release
     * @return completion stage yielding the owned lease if acquired, empty otherwise
     */
    CompletionStage<Optional<TransferLease>> tryLockAsync(UUID playerId, Duration duration);

    /**
     * Releases the player lock across the network.
     *
     * @param lease owned lease returned by {@link #tryLockAsync(UUID, Duration)}
     * @return completion stage completed upon lock release
     */
    CompletionStage<Void> unlockAsync(TransferLease lease);

    /**
     * Returns a no-op lock implementation for single-server standalone deployments.
     *
     * @return no-op transfer lock
     */
    static CrossServerTransferLock noop() {
        return NoopTransferLock.INSTANCE;
    }

    /**
     * No-op lock implementation.
     */
    final class NoopTransferLock implements CrossServerTransferLock {
        private static final NoopTransferLock INSTANCE = new NoopTransferLock();

        private NoopTransferLock() {}

        @Override
        public CompletionStage<Optional<TransferLease>> tryLockAsync(UUID playerId, Duration duration) {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(duration, "duration");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("duration must be positive");
            }
            return CompletableFuture.completedFuture(
                    Optional.of(new TransferLease(playerId, UUID.randomUUID().toString())));
        }

        @Override
        public CompletionStage<Void> unlockAsync(TransferLease lease) {
            Objects.requireNonNull(lease, "lease");
            return CompletableFuture.completedFuture(null);
        }
    }
}

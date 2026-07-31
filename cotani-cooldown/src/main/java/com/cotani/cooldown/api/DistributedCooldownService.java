package com.cotani.cooldown.api;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Non-blocking cooldown service backed by a shared database.
 *
 * <p>{@link #checkAndStartAsync} is atomic across service/process instances that use the same
 * database table. All methods perform I/O and therefore return {@link CompletionStage}.
 */
public interface DistributedCooldownService extends AutoCloseable {
    CompletionStage<CooldownResult> checkAndStartAsync(CooldownKey key, Duration duration);

    CompletionStage<Optional<CooldownEntry>> findAsync(CooldownKey key);

    CompletionStage<Void> removeAsync(CooldownKey key);

    CompletionStage<Void> clearExpiredAsync();

    CompletionStage<Void> clearAllAsync();

    CompletionStage<Long> sizeAsync();

    @Override
    void close();
}

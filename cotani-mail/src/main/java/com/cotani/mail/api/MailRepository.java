package com.cotani.mail.api;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous persistence SPI for idempotent mail messages.
 *
 * <p>Implementations must not access Bukkit objects and must preserve message content for a reused {@link MailId}.
 * Repository calls may run on storage-owned executors; none of these methods may block the caller.
 */
public interface MailRepository {
    /**
     * Persists one message. Repeating an equal id is a successful no-op; reusing an id with different data fails.
     */
    CompletionStage<MailMessage> saveAsync(MailMessage message);

    /** Lists a recipient's non-expired messages in newest-first order at the supplied instant. */
    CompletionStage<MailPage> inboxAsync(UUID recipientId, MailQuery query, Instant now);

    /** Marks a recipient-owned message as read. */
    CompletionStage<MailMessage> markReadAsync(UUID recipientId, MailId id, Instant readAt);

    /** Deletes a recipient-owned message. */
    CompletionStage<Void> deleteAsync(UUID recipientId, MailId id);

    /** Removes expired messages and completes when cleanup is durable. */
    CompletionStage<Void> purgeExpiredAsync(Instant now);
}

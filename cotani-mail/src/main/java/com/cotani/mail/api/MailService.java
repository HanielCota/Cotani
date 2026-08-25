package com.cotani.mail.api;

import com.cotani.AsyncCloseable;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous persistent inbox and player-to-player mail use cases.
 *
 * <p>Operations never block. Mutations are serialized per service and subsequent inbox reads wait for previously
 * accepted mutations, so a successful mutation is visible to a later read. A repository timeout only completes the
 * caller-facing stage; the internal barrier remains active until the repository operation finishes.
 */
public interface MailService extends AsyncCloseable, AutoCloseable {
    /** Sends an idempotent request using the timestamp and TTL embedded in the request. */
    CompletionStage<MailMessage> sendAsync(MailSendRequest request);

    /** Sends a new message using the configured default TTL. */
    CompletionStage<MailMessage> sendAsync(UUID senderId, UUID recipientId, String subject, String body);

    /** Loads a bounded recipient inbox; expired messages are omitted. */
    CompletionStage<MailPage> inboxAsync(UUID recipientId, MailQuery query);

    /** Marks one recipient-owned message as read. */
    CompletionStage<MailMessage> markReadAsync(UUID recipientId, MailId id);

    /** Deletes one recipient-owned message. */
    CompletionStage<Void> deleteAsync(UUID recipientId, MailId id);

    /** Purges expired messages from the repository. */
    CompletionStage<Void> purgeExpiredAsync();

    /** Starts asynchronous shutdown and rejects new operations. */
    @Override
    void close();
}

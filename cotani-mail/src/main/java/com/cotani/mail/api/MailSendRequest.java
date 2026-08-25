package com.cotani.mail.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Idempotent request for sending one mail message. */
public record MailSendRequest(
        MailId id, UUID senderId, UUID recipientId, String subject, String body, Instant sentAt, Duration timeToLive) {
    public MailSendRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        subject = MailMessage.normalizeText(subject, "subject", MailMessage.MAX_SUBJECT_LENGTH);
        body = MailMessage.normalizeText(body, "body", MailMessage.MAX_BODY_LENGTH);
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(timeToLive, "timeToLive");
        if (timeToLive.isZero() || timeToLive.isNegative()) {
            throw new IllegalArgumentException("timeToLive must be positive");
        }
    }

    /** Creates a request with a fresh idempotency key. */
    public static MailSendRequest create(
            UUID senderId, UUID recipientId, String subject, String body, Duration timeToLive) {
        return create(senderId, recipientId, subject, body, timeToLive, Instant.now());
    }

    /** Creates a request with a caller-supplied timestamp for deterministic retries and tests. */
    public static MailSendRequest create(
            UUID senderId, UUID recipientId, String subject, String body, Duration timeToLive, Instant sentAt) {
        return new MailSendRequest(MailId.random(), senderId, recipientId, subject, body, sentAt, timeToLive);
    }

    /** Materializes this request with unread state. */
    public MailMessage toMessage() {
        try {
            return new MailMessage(id, senderId, recipientId, subject, body, sentAt, sentAt.plus(timeToLive), false);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("timeToLive is too large", failure);
        }
    }
}

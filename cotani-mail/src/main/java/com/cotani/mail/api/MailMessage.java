package com.cotani.mail.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable mail message with bounded text and an explicit expiration time. */
public record MailMessage(
        MailId id,
        UUID senderId,
        UUID recipientId,
        String subject,
        String body,
        Instant sentAt,
        Instant expiresAt,
        boolean read) {
    public static final int MAX_SUBJECT_LENGTH = 128;
    public static final int MAX_BODY_LENGTH = 4_096;

    public MailMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(recipientId, "recipientId");
        subject = normalizeText(subject, "subject", MAX_SUBJECT_LENGTH);
        body = normalizeText(body, "body", MAX_BODY_LENGTH);
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(sentAt)) {
            throw new IllegalArgumentException("expiresAt must be after sentAt");
        }
    }

    /** Returns an equivalent message marked as read. */
    public MailMessage markRead() {
        return read ? this : new MailMessage(id, senderId, recipientId, subject, body, sentAt, expiresAt, true);
    }

    /** Returns whether the message is expired at the supplied instant. */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(Objects.requireNonNull(now, "now"));
    }

    /** Returns whether two messages represent the same idempotent send, ignoring read state. */
    public boolean hasSameContentAs(MailMessage other) {
        Objects.requireNonNull(other, "other");
        return id.equals(other.id)
                && senderId.equals(other.senderId)
                && recipientId.equals(other.recipientId)
                && subject.equals(other.subject)
                && body.equals(other.body)
                && sentAt.equals(other.sentAt)
                && expiresAt.equals(other.expiresAt);
    }

    static String normalizeText(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        var normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must contain at most " + maximumLength + " characters");
        }
        return normalized;
    }
}

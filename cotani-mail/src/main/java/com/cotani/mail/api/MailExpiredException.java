package com.cotani.mail.api;

import java.time.Instant;
import java.util.Objects;

/** Raised when a send request has already expired before it can be persisted. */
public final class MailExpiredException extends MailException {
    private static final long serialVersionUID = 1L;
    private final transient MailId id;
    private final Instant expiresAt;

    public MailExpiredException(MailId id, Instant expiresAt) {
        super("Mail request has expired: " + Objects.requireNonNull(id, "id"));
        this.id = id;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public MailId id() {
        return id;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}

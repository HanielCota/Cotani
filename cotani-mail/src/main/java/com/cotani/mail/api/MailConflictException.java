package com.cotani.mail.api;

import java.util.Objects;

/** Raised when an idempotency key is reused with different message data. */
public final class MailConflictException extends MailException {
    private static final long serialVersionUID = 1L;
    private final transient MailId id;

    public MailConflictException(MailId id) {
        super("Mail id was already used with different data: " + Objects.requireNonNull(id, "id"));
        this.id = id;
    }

    public MailId id() {
        return id;
    }
}

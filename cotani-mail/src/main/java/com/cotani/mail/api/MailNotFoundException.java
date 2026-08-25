package com.cotani.mail.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when a message is absent or does not belong to the requested recipient. */
public final class MailNotFoundException extends MailException {
    private static final long serialVersionUID = 1L;
    private final transient UUID recipientId;
    private final transient MailId id;

    public MailNotFoundException(UUID recipientId, MailId id) {
        super("Mail not found for recipient " + Objects.requireNonNull(recipientId, "recipientId") + ": "
                + Objects.requireNonNull(id, "id"));
        this.recipientId = recipientId;
        this.id = id;
    }

    public UUID recipientId() {
        return recipientId;
    }

    public MailId id() {
        return id;
    }
}

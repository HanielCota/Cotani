package com.cotani.mail.api;

import java.util.Objects;
import java.util.UUID;

/** Stable idempotency key for one mail message. */
public record MailId(UUID value) {
    public MailId {
        Objects.requireNonNull(value, "value");
    }

    public static MailId random() {
        return new MailId(UUID.randomUUID());
    }
}

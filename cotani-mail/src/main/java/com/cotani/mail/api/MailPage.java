package com.cotani.mail.api;

import java.util.List;
import java.util.Objects;

/** Immutable paginated inbox result. */
public record MailPage(List<MailMessage> messages, boolean hasMore, int unreadCount) {
    public MailPage {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        messages.forEach(message -> Objects.requireNonNull(message, "message"));
        if (unreadCount < 0) {
            throw new IllegalArgumentException("unreadCount must not be negative");
        }
    }
}

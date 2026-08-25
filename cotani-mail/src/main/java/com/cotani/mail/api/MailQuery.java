package com.cotani.mail.api;

/** Bounded page query for a recipient's inbox. Pages are zero-based and newest messages come first. */
public record MailQuery(int page, int pageSize, boolean unreadOnly) {
    public MailQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
    }

    public static MailQuery firstPage(int pageSize) {
        return new MailQuery(0, pageSize, false);
    }

    public MailQuery unread() {
        return new MailQuery(page, pageSize, true);
    }
}

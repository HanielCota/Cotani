package com.cotani.mail.api;

/** Base exception for expected mail-domain failures. */
public class MailException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MailException(String message) {
        super(message);
    }
}

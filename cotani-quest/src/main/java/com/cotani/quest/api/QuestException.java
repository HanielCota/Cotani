package com.cotani.quest.api;

/** Base exception for expected quest-domain failures. */
public class QuestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public QuestException(String message) {
        super(message);
    }

    public QuestException(String message, Throwable cause) {
        super(message, cause);
    }
}

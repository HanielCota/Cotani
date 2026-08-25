package com.cotani.audit.api;

/** Signals that a bounded audit repository cannot accept another entry. */
public final class AuditCapacityExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int capacity;

    public AuditCapacityExceededException(int capacity) {
        super("Audit repository capacity exceeded: " + capacity);
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }
}

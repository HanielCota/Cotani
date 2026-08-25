package com.cotani.job.api;

/** Raised when a job references a handler that has not been registered. */
public final class JobHandlerNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String handlerName;

    public JobHandlerNotFoundException(String handlerName) {
        super("No job handler is registered for: " + handlerName);
        this.handlerName = java.util.Objects.requireNonNull(handlerName, "handlerName");
    }

    public String handlerName() {
        return handlerName;
    }
}

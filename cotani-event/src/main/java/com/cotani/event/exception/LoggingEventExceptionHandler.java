package com.cotani.event.exception;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoggingEventExceptionHandler implements EventExceptionHandler {

    private final Logger logger;

    public LoggingEventExceptionHandler(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    public static LoggingEventExceptionHandler usingJavaLogger() {
        return new LoggingEventExceptionHandler(Logger.getLogger("cotani-event"));
    }

    @Override
    public void handle(EventListenerException exception) {
        Objects.requireNonNull(exception, "exception cannot be null");

        logger.log(Level.SEVERE, exception.getMessage(), exception);
    }
}

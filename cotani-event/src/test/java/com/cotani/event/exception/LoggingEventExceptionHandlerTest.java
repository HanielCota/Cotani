package com.cotani.event.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventPriority;
import com.cotani.event.subscription.DefaultEventSubscription;
import com.cotani.event.subscription.EventSubscription;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

final class LoggingEventExceptionHandlerTest {

    @Test
    void shouldLogFailureAtSevereLevelWithMessageAndThrown() {
        var records = new ArrayList<LogRecord>();
        Logger logger = Logger.getLogger("cotani-event.test.logging");
        boolean useParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        Handler capturing = capturingHandler(records);
        logger.addHandler(capturing);
        try {
            LoggingEventExceptionHandler handler = new LoggingEventExceptionHandler(logger);
            EventListenerException exception =
                    new EventListenerException(new TestEvent(), subscription(), new IllegalStateException("boom"));

            handler.handle(exception);

            assertEquals(1, records.size());
            LogRecord record = records.getFirst();
            assertEquals(Level.SEVERE, record.getLevel());
            assertEquals("Failed to dispatch event TestEvent", record.getMessage());
            assertSame(exception, record.getThrown());
        } finally {
            logger.setUseParentHandlers(useParentHandlers);
            logger.removeHandler(capturing);
        }
    }

    @Test
    void shouldLogThroughDefaultCotaniEventLogger() {
        var records = new ArrayList<LogRecord>();
        Logger logger = Logger.getLogger("cotani-event");
        boolean useParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        Handler capturing = capturingHandler(records);
        logger.addHandler(capturing);
        try {
            LoggingEventExceptionHandler handler = LoggingEventExceptionHandler.usingJavaLogger();
            EventListenerException exception =
                    new EventListenerException(new TestEvent(), subscription(), new IllegalStateException("boom"));

            handler.handle(exception);

            assertEquals(1, records.size());
            assertSame(exception, records.getFirst().getThrown());
        } finally {
            logger.setUseParentHandlers(useParentHandlers);
            logger.removeHandler(capturing);
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullException() {
        LoggingEventExceptionHandler handler =
                new LoggingEventExceptionHandler(Logger.getLogger("cotani-event.test.null"));

        assertThrows(NullPointerException.class, () -> handler.handle(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullLogger() {
        assertThrows(NullPointerException.class, () -> new LoggingEventExceptionHandler(null));
    }

    private static Handler capturingHandler(List<LogRecord> records) {
        return new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
    }

    private static EventSubscription subscription() {
        return DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {});
    }

    private record TestEvent() implements CotaniEvent {}
}

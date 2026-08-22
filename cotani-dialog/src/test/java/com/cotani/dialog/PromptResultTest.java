package com.cotani.dialog;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.dialog.api.CancelReason;
import com.cotani.dialog.api.PromptResult;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PromptResultTest {

    @Test
    void shouldHandleSuccessResult() {
        var result = PromptResult.success(100);

        assertTrue(result.isSuccess());
        assertFalse(result.isCancelled());
        assertFalse(result.isError());
        assertEquals(100, result.valueOrThrow());
        assertEquals(100, result.valueOrElse(0));
        assertTrue(result.valueOptional().isPresent());
        assertEquals(100, result.valueOptional().get());

        var flag = new AtomicBoolean();
        result.ifSuccess(val -> {
            assertEquals(100, val);
            flag.set(true);
        });
        assertTrue(flag.get());

        var mapped = result.map(val -> "$" + val);
        assertTrue(mapped.isSuccess());
        assertEquals("$100", mapped.valueOrThrow());
    }

    @Test
    void shouldHandleCancelledResult() {
        var result = PromptResult.<Integer>cancelled(CancelReason.TIMEOUT);

        assertFalse(result.isSuccess());
        assertTrue(result.isCancelled());
        assertFalse(result.isError());
        assertEquals(0, result.valueOrElse(0));
        assertTrue(result.valueOptional().isEmpty());
        assertThrows(java.util.NoSuchElementException.class, result::valueOrThrow);

        var flag = new AtomicBoolean();
        result.ifCancelled(reason -> {
            assertEquals(CancelReason.TIMEOUT, reason);
            flag.set(true);
        });
        assertTrue(flag.get());

        var mapped = result.map(String::valueOf);
        assertTrue(mapped.isCancelled());
    }

    @Test
    void shouldHandleErrorResult() {
        var ex = new RuntimeException("boom");
        var result = PromptResult.<String>error(ex);

        assertFalse(result.isSuccess());
        assertFalse(result.isCancelled());
        assertTrue(result.isError());
        assertThrows(IllegalStateException.class, result::valueOrThrow);
    }
}

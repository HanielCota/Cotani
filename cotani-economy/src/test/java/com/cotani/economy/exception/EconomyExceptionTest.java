package com.cotani.economy.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.transaction.EconomyOperationId;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class EconomyExceptionTest {

    @Test
    void shouldExposeMessageFromSimpleConstructor() {
        var exception = new EconomyException("boom");

        assertEquals("boom", exception.getMessage());
    }

    @Test
    void shouldPreserveCauseFromMessageAndCauseConstructor() {
        var cause = new IllegalStateException("root");
        var exception = new EconomyException("boom", cause);

        assertEquals("boom", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void shouldFormatInsufficientFundsExceptionMessage() {
        var userId = UUID.randomUUID();
        var exception = new InsufficientFundsException(userId, new BigDecimal("10.00"), new BigDecimal("25.00"));

        assertInstanceOf(EconomyException.class, exception);
        assertTrue(exception.getMessage().contains(userId.toString()));
        assertTrue(exception.getMessage().contains("10.00"));
        assertTrue(exception.getMessage().contains("25.00"));
    }

    @Test
    void shouldFormatInvalidAmountExceptionMessage() {
        var exception = new InvalidAmountException(new BigDecimal("-5.00"), "amount must be greater than zero");

        assertInstanceOf(EconomyException.class, exception);
        assertTrue(exception.getMessage().contains("-5.00"));
        assertTrue(exception.getMessage().contains("amount must be greater than zero"));
    }

    @Test
    void shouldFormatMaximumBalanceExceededExceptionMessage() {
        var userId = UUID.randomUUID();
        var exception =
                new MaximumBalanceExceededException(userId, new BigDecimal("1000.01"), new BigDecimal("1000.00"));

        assertInstanceOf(EconomyException.class, exception);
        assertTrue(exception.getMessage().contains(userId.toString()));
        assertTrue(exception.getMessage().contains("1000.01"));
        assertTrue(exception.getMessage().contains("1000.00"));
    }

    @Test
    void shouldFormatDuplicateEconomyOperationExceptionMessage() {
        var operationId = EconomyOperationId.random();
        var exception = new DuplicateEconomyOperationException(operationId);

        assertInstanceOf(EconomyException.class, exception);
        assertTrue(exception.getMessage().contains(operationId.value().toString()));
    }

    @Test
    void shouldFormatSameEconomyAccountTransferExceptionMessage() {
        var userId = UUID.randomUUID();
        var exception = new SameEconomyAccountTransferException(userId);

        assertInstanceOf(EconomyException.class, exception);
        assertTrue(exception.getMessage().contains(userId.toString()));
    }
}

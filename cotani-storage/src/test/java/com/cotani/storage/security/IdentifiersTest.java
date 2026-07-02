package com.cotani.storage.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void acceptsValidIdentifier() {
        assertEquals("my_table", Identifiers.requireValid("my_table"));
    }

    @Test
    void acceptsIdentifierWithUnderscore() {
        assertEquals("my_table_1", Identifiers.requireValid("my_table_1"));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid(""));
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid(null));
    }

    @Test
    void rejectsStartingWithNumber() {
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid("1table"));
    }

    @Test
    void rejectsSpecialCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid("my-table"));
    }

    @Test
    void rejectsSqlKeyword() {
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid("select"));
    }

    @Test
    void rejectsSqlKeywordCaseInsensitive() {
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid("SELECT"));
    }

    @Test
    void rejectsExceedingMaxLength() {
        var longName = "a".repeat(65);
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid(longName));
    }

    @Test
    void acceptsMaxLength() {
        var maxName = "a".repeat(64);
        assertEquals(maxName, Identifiers.requireValid(maxName));
    }

    @Test
    void quoteWrapsWithQuotes() {
        assertEquals("\"my_col\"", Identifiers.quote("my_col", '"'));
    }

    @Test
    @SuppressWarnings("NullAway")
    void requireValidWithContextIncludesContextInMessage() {
        var ex = assertThrows(IllegalArgumentException.class, () -> Identifiers.requireValid("drop", "Table name"));
        assertTrue(ex.getMessage().contains("Table name"));
    }
}

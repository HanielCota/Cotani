package com.cotani.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Extra {@link Structure} scenarios not covered by {@link StructureTest}: symbol exposure,
 * immutable results, whitespace-only rows and null validation.
 */
final class StructureAdditionalTest {
    @Test
    void shouldExposeImmutableSymbolSet() {
        var structure = Structure.parse("X X", "F F");

        assertEquals(Set.of('X', 'F'), structure.symbols());
        assertThrows(
                UnsupportedOperationException.class, () -> structure.symbols().add('Z'));
    }

    @Test
    void shouldReturnImmutableSlotLists() {
        var structure = Structure.parse("X X X");

        assertEquals(List.of(0, 1, 2), structure.slots('X'));
        assertThrows(
                UnsupportedOperationException.class, () -> structure.slots('X').add(99));
    }

    @Test
    void shouldSkipWhitespaceOnlyRows() {
        var structure = Structure.parse("   ", "X");

        assertEquals(2, structure.rows());
        assertEquals(18, structure.size());
        assertEquals(List.of(9), structure.slots('X'));
    }

    @Test
    void shouldAcceptSingleTokenStructure() {
        var structure = Structure.parse("A");

        assertEquals(1, structure.rows());
        assertEquals(9, structure.size());
        assertEquals(List.of(0), structure.slots('A'));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullRowElements() {
        assertThrows(NullPointerException.class, () -> Structure.parse("X", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullRowsArray() {
        assertThrows(NullPointerException.class, () -> Structure.parse((String[]) null));
        assertEquals(Structure.EMPTY, '.');
    }
}

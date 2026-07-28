package com.cotani.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class StructureTest {

    @Test
    void parsesSymbolsToSlotCoordinates() {
        var structure = Structure.parse("X X X", "X . X");

        assertEquals(List.of(0, 1, 2, 9, 11), structure.slots('X'));
        assertEquals(2, structure.rows());
        assertEquals(18, structure.size());
    }

    @Test
    void skipsEmptySlotsAndUnknownSymbolsReturnEmpty() {
        var structure = Structure.parse(". . .", ". F .");

        assertEquals(List.of(10), structure.slots('F'));
        assertTrue(structure.slots('.').isEmpty());
        assertTrue(structure.slots('Z').isEmpty());
    }

    @Test
    void keepsReadingOrderForRepeatedSymbols() {
        var structure = Structure.parse("# . # . #", ". # . # .", "# . # . #");

        assertEquals(List.of(0, 2, 4, 10, 12, 18, 20, 22), structure.slots('#'));
    }

    @Test
    void rejectsMultiCharacterTokens() {
        assertThrows(IllegalArgumentException.class, () -> Structure.parse("XX X X"));
    }

    @Test
    void rejectsRowsWithMoreThanNineColumns() {
        assertThrows(IllegalArgumentException.class, () -> Structure.parse("X X X X X X X X X X"));
    }

    @Test
    void rejectsInvalidRowCounts() {
        assertThrows(IllegalArgumentException.class, Structure::parse);
        assertThrows(IllegalArgumentException.class, () -> Structure.parse("X", "X", "X", "X", "X", "X", "X"));
    }
}

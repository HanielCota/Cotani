package com.cotani.gui.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parsed matrix layout that maps symbols to inventory slots.
 *
 * <p>Each row is a whitespace-separated list of single-character tokens, at most 9 tokens per row and
 * 6 rows per structure. The slot of a token at row {@code r} and column {@code c} is {@code r * 9 + c}.
 * The character {@code '.'} is reserved for intentionally empty slots and is never recorded.
 */
public final class Structure {
    /**
     * Reserved symbol for intentionally empty slots.
     */
    public static final char EMPTY = '.';

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    private final Map<Character, List<Integer>> slotsBySymbol;
    private final int rows;

    private Structure(Map<Character, List<Integer>> slotsBySymbol, int rows) {
        this.slotsBySymbol = slotsBySymbol;
        this.rows = rows;
    }

    /**
     * Parses row patterns such as {@code "# # #"} and {@code "# F #"} into a structure.
     *
     * @param rows the row patterns, between 1 and 6 entries
     * @return the parsed structure
     * @throws IllegalArgumentException when a row has more than 9 tokens, a token is not a single
     *     character, or the row count is outside {@code 1..6}
     */
    public static Structure parse(String... rows) {
        Objects.requireNonNull(rows, "Parameter 'rows' must not be null");

        if (rows.length == 0 || rows.length > MAX_ROWS) {
            throw new IllegalArgumentException("Structure must have between 1 and " + MAX_ROWS + " rows");
        }

        Map<Character, List<Integer>> slotsBySymbol = new LinkedHashMap<>();

        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            var row = Objects.requireNonNull(rows[rowIndex], "Structure row must not be null")
                    .trim();
            if (row.isEmpty()) {
                continue;
            }

            var tokens = tokenize(row);

            if (tokens.size() > COLUMNS) {
                throw new IllegalArgumentException(
                        "Structure row " + rowIndex + " has " + tokens.size() + " columns; maximum is " + COLUMNS);
            }

            for (int column = 0; column < tokens.size(); column++) {
                var token = tokens.get(column);

                if (token.length() != 1) {
                    throw new IllegalArgumentException("Structure token '" + token + "' at row " + rowIndex
                            + ", column " + column + " must be a single character");
                }

                var symbol = token.charAt(0);

                if (symbol == EMPTY) {
                    continue;
                }

                slotsBySymbol.computeIfAbsent(symbol, _ -> new ArrayList<>()).add(rowIndex * COLUMNS + column);
            }
        }

        Map<Character, List<Integer>> immutable = new LinkedHashMap<>();
        slotsBySymbol.forEach((symbol, slots) -> immutable.put(symbol, List.copyOf(slots)));

        return new Structure(Map.copyOf(immutable), rows.length);
    }

    private static List<String> tokenize(String row) {
        List<String> tokens = new ArrayList<>();

        var index = 0;

        while (index < row.length()) {
            while (index < row.length() && Character.isWhitespace(row.charAt(index))) {
                index++;
            }

            var start = index;

            while (index < row.length() && !Character.isWhitespace(row.charAt(index))) {
                index++;
            }

            if (start < index) {
                tokens.add(row.substring(start, index));
            }
        }

        return tokens;
    }

    /**
     * Returns the number of rows declared by this structure.
     *
     * @return the row count, between 1 and 6
     */
    public int rows() {
        return rows;
    }

    /**
     * Returns the total inventory size required by this structure ({@code rows * 9}).
     *
     * @return the slot count
     */
    public int size() {
        return rows * COLUMNS;
    }

    /**
     * Returns the slots assigned to the given symbol, in reading order.
     *
     * @param symbol the structure symbol
     * @return an immutable list of slots, empty when the symbol is absent
     */
    public List<Integer> slots(char symbol) {
        return slotsBySymbol.getOrDefault(symbol, List.of());
    }

    /**
     * Returns all symbols declared by this structure.
     *
     * @return an immutable set of symbols
     */
    public Set<Character> symbols() {
        return slotsBySymbol.keySet();
    }
}

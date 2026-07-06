package com.cotani.storage.security;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class Identifiers {

    private static final int MAX_LENGTH = 64;
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[A-Za-z_]\\w*$");

    private static final Set<String> SQL_KEYWORDS = Set.of(
            "select",
            "insert",
            "update",
            "delete",
            "from",
            "where",
            "into",
            "values",
            "table",
            "create",
            "drop",
            "alter",
            "index",
            "view",
            "join",
            "on",
            "and",
            "or",
            "not",
            "null",
            "is",
            "in",
            "between",
            "like",
            "as",
            "order",
            "by",
            "group",
            "having",
            "limit",
            "offset",
            "distinct",
            "primary",
            "key",
            "foreign",
            "references",
            "constraint",
            "unique",
            "default",
            "check",
            "case",
            "when",
            "then",
            "else",
            "end",
            "union",
            "all",
            "exists",
            "some",
            "any",
            "with",
            "recursive",
            "begin",
            "commit",
            "rollback",
            "transaction",
            "set",
            "grant",
            "revoke",
            "column",
            "database",
            "schema",
            "if",
            "cast",
            "convert");

    private Identifiers() {}

    public static String requireValid(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank.");
        }

        if (identifier.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Identifier exceeds maximum length of " + MAX_LENGTH + ": " + identifier);
        }

        if (!VALID_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }

        if (SQL_KEYWORDS.contains(identifier.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Identifier must not be a SQL keyword: " + identifier);
        }

        return identifier;
    }

    public static String requireValid(String identifier, String context) {
        try {
            return requireValid(identifier);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(context + " " + exception.getMessage(), exception);
        }
    }

    public static String quote(String identifier, char quote) {
        requireValid(identifier);
        return quote + identifier + quote;
    }
}

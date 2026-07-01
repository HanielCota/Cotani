package br.com.cotani.storage.security;

import java.util.regex.Pattern;

public final class Identifiers {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[A-Za-z_]\\w*$");

    private Identifiers() {}

    public static String requireValid(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank.");
        }

        if (!VALID_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
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

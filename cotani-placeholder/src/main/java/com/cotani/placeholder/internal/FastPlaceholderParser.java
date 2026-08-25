package com.cotani.placeholder.internal;

import com.cotani.api.InternalApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * High-performance string token scanner and replacer.
 *
 * <p>Supports both `%token%` and `{token}` delimiters in a single pass without regex compilation.
 */
@InternalApi
@NullMarked
public final class FastPlaceholderParser {

    private static final int MAX_TOKEN_LENGTH = 128;

    private FastPlaceholderParser() {}

    /**
     * Represents an extracted token occurrence inside a template string.
     */
    public record TokenMatch(int startIndex, int endIndex, String fullToken, String innerToken, char delimiter) {}

    /**
     * Finds all placeholder tokens within the given input text.
     *
     * @param text input text
     * @return list of token matches in order
     */
    public static List<TokenMatch> findTokens(String text) {
        Objects.requireNonNull(text, "Parameter 'text' must not be null");

        var length = text.length();
        if (length < 3) {
            return List.of();
        }

        List<TokenMatch> matches = new ArrayList<>();
        var i = 0;

        while (i < length) {
            char ch = text.charAt(i);

            if (ch == '%' || ch == '{') {
                char closeChar = (ch == '%') ? '%' : '}';
                int closeIndex = text.indexOf(closeChar, i + 1);

                if (closeIndex > i + 1 && (closeIndex - i) <= MAX_TOKEN_LENGTH) {
                    // Check if inner content contains whitespace or newlines (invalid placeholder)
                    var valid = true;
                    for (int k = i + 1; k < closeIndex; k++) {
                        char c = text.charAt(k);
                        if (Character.isWhitespace(c) || c == '\n' || c == '\r') {
                            valid = false;
                            break;
                        }
                    }

                    if (valid) {
                        String fullToken = text.substring(i, closeIndex + 1);
                        String innerToken = text.substring(i + 1, closeIndex);
                        matches.add(new TokenMatch(i, closeIndex + 1, fullToken, innerToken, ch));
                        i = closeIndex + 1;
                        continue;
                    }
                }
            }
            i++;
        }

        return List.copyOf(matches);
    }

    /**
     * Splits a placeholder inner token (e.g. "player_name" or "coins:balance") into identifier and parameters.
     *
     * @param token inner token string
     * @return array with 2 elements: [identifier, params]
     */
    public static String[] splitIdentifierAndParams(String token) {
        Objects.requireNonNull(token, "Parameter 'token' must not be null");

        // First check for colon delimiter (e.g. "prefix:params")
        int colonIndex = token.indexOf(':');
        if (colonIndex != -1) {
            return new String[] {
                token.substring(0, colonIndex).toLowerCase(java.util.Locale.ROOT), token.substring(colonIndex + 1)
            };
        }

        // Check for underscore delimiter (e.g. "player_name" -> "player", "name")
        int underscoreIndex = token.indexOf('_');
        if (underscoreIndex != -1) {
            return new String[] {
                token.substring(0, underscoreIndex).toLowerCase(java.util.Locale.ROOT),
                token.substring(underscoreIndex + 1)
            };
        }

        return new String[] {token.toLowerCase(java.util.Locale.ROOT), ""};
    }

    /**
     * Replaces tokens in the input text using a replacer function.
     *
     * @param text input text
     * @param replacer function that converts an inner token to its replacement string (or null to keep unchanged)
     * @return parsed output string
     */
    public static String replaceTokens(String text, Function<String, @Nullable String> replacer) {
        Objects.requireNonNull(text, "Parameter 'text' must not be null");
        Objects.requireNonNull(replacer, "Parameter 'replacer' must not be null");

        List<TokenMatch> tokens = findTokens(text);
        if (tokens.isEmpty()) {
            return text;
        }

        var sb = new StringBuilder(text.length() + 32);
        var lastIndex = 0;

        for (TokenMatch token : tokens) {
            sb.append(text, lastIndex, token.startIndex());
            String replacement = replacer.apply(token.innerToken());

            if (replacement == null) {
                sb.append(token.fullToken());
                lastIndex = token.endIndex();
                continue;
            }

            sb.append(replacement);

            lastIndex = token.endIndex();
        }

        if (lastIndex < text.length()) {
            sb.append(text, lastIndex, text.length());
        }

        return sb.toString();
    }
}

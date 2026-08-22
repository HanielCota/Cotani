package com.cotani.command.internal;

import com.cotani.api.InternalApi;
import com.cotani.command.argument.ParseContext;
import com.cotani.command.argument.ParseResult;
import java.util.Objects;

/**
 * Internal reader for parsing unquoted or quoted strings (single/double quotes) spanning multiple tokens.
 */
@InternalApi
public final class QuotedStringReader {
    private QuotedStringReader() {}

    public static ParseResult<String> read(ParseContext context) {
        Objects.requireNonNull(context, "context");
        if (context.isExhausted()) {
            return ParseResult.failure("<red>Missing required string argument.</red>");
        }

        var first = context.currentArg();
        if (first.isEmpty()) {
            return ParseResult.success("", 1);
        }

        char firstChar = first.charAt(0);
        if (firstChar != '"' && firstChar != '\'') {
            return ParseResult.success(first, 1);
        }

        char quoteChar = firstChar;
        var rawArgs = context.rawArgs();
        var index = context.currentIndex();

        // Check if single-word quoted: "hello"
        if (first.length() >= 2
                && first.charAt(first.length() - 1) == quoteChar
                && (first.length() == 2 || first.charAt(first.length() - 2) != '\\')) {
            var content = first.substring(1, first.length() - 1).replace("\\" + quoteChar, String.valueOf(quoteChar));
            return ParseResult.success(content, 1);
        }

        var sb = new StringBuilder();
        if (first.length() > 1) {
            sb.append(first.substring(1));
        }
        var consumed = 1;
        var closed = false;

        for (int i = index + 1; i < rawArgs.size(); i++) {
            consumed++;
            var arg = rawArgs.get(i);
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            if (arg.endsWith(String.valueOf(quoteChar))
                    && (arg.length() == 1 || arg.charAt(arg.length() - 2) != '\\')) {
                sb.append(arg, 0, arg.length() - 1);
                closed = true;
                break;
            }
            sb.append(arg);
        }

        if (!closed) {
            return ParseResult.failure("<red>Unclosed quote for argument: " + quoteChar + "</red>");
        }

        var unescaped = sb.toString().replace("\\" + quoteChar, String.valueOf(quoteChar));
        return ParseResult.success(unescaped, consumed);
    }
}

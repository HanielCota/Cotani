package com.cotani.command.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.cotani.command.argument.ParseContext;
import com.cotani.command.argument.ParseResult;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class QuotedStringReaderTest {

    @Test
    void shouldReadSingleUnquotedWord() {
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("hello", "world"), 0);

        var result = QuotedStringReader.read(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        var success = (ParseResult.Success<String>) result;
        assertEquals("hello", success.value());
        assertEquals(1, success.consumedArgs());
    }

    @Test
    void shouldReadSingleWordQuoted() {
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("\"hello\"", "world"), 0);

        var result = QuotedStringReader.read(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        var success = (ParseResult.Success<String>) result;
        assertEquals("hello", success.value());
        assertEquals(1, success.consumedArgs());
    }

    @Test
    void shouldReadMultiWordQuotedString() {
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("\"hello", "brave", "new", "world\"", "extra"), 0);

        var result = QuotedStringReader.read(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        var success = (ParseResult.Success<String>) result;
        assertEquals("hello brave new world", success.value());
        assertEquals(4, success.consumedArgs());
    }

    @Test
    void shouldFailOnUnclosedQuotes() {
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("\"unclosed", "string"), 0);

        var result = QuotedStringReader.read(ctx);
        assertInstanceOf(ParseResult.Failure.class, result);
    }

    @Test
    void shouldReadSingleQuotedString() {
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("'single", "quoted", "text'"), 0);

        var result = QuotedStringReader.read(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        var success = (ParseResult.Success<String>) result;
        assertEquals("single quoted text", success.value());
        assertEquals(3, success.consumedArgs());
    }

    @Test
    void shouldReadQuotedStringWithOffsetIndex() {
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("ignored", "\"target", "value\""), 1);

        var result = QuotedStringReader.read(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        var success = (ParseResult.Success<String>) result;
        assertEquals("target value", success.value());
        assertEquals(2, success.consumedArgs());
    }

    @Test
    void shouldFailWhenOutOfBoundsOrEmpty() {
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("word"), 1);

        var result = QuotedStringReader.read(ctx);
        assertInstanceOf(ParseResult.Failure.class, result);
    }
}

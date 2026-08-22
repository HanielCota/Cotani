package com.cotani.command.argument;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class ArgumentsTest {

    private enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    @Test
    void shouldParseStringArgument() {
        var arg = Arguments.string("name");
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("test"), 0);

        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals("test", ((ParseResult.Success<String>) result).value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseQuotedStringArgument() {
        var arg = Arguments.quotedString("text");
        var sender = mock(CommandSender.class);

        // Single word quoted
        var ctx1 = new ParseContext(sender, List.of("\"hello\""), 0);
        var res1 = (ParseResult.Success<String>) arg.parser().parse(ctx1);
        assertEquals("hello", res1.value());
        assertEquals(1, res1.consumedArgs());

        // Multi word quoted across tokens
        var ctx2 = new ParseContext(sender, List.of("\"hello", "world\""), 0);
        var res2 = (ParseResult.Success<String>) arg.parser().parse(ctx2);
        assertEquals("hello world", res2.value());
        assertEquals(2, res2.consumedArgs());

        // Isolated quote tokens
        var ctx3 = new ParseContext(sender, List.of("\"", "hello", "world\""), 0);
        var res3 = (ParseResult.Success<String>) arg.parser().parse(ctx3);
        assertEquals("hello world", res3.value());
        assertEquals(3, res3.consumedArgs());

        // Escaped quotes inside
        var ctx4 = new ParseContext(sender, List.of("\"hello", "\\\"world\\\"\""), 0);
        var res4 = (ParseResult.Success<String>) arg.parser().parse(ctx4);
        assertEquals("hello \"world\"", res4.value());
    }

    @Test
    void shouldParseGreedyStringArgument() {
        var arg = Arguments.greedyString("message");
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("this", "is", "a", "long", "message"), 0);

        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        var success = (ParseResult.Success<String>) result;
        assertEquals("this is a long message", success.value());
        assertEquals(5, success.consumedArgs());
    }

    @Test
    void shouldParseIntegerArgument() {
        var arg = Arguments.integer("amount", 1, 100);
        var sender = mock(CommandSender.class);

        var validCtx = new ParseContext(sender, List.of("50"), 0);
        var validResult = arg.parser().parse(validCtx);
        assertInstanceOf(ParseResult.Success.class, validResult);
        assertEquals(50, ((ParseResult.Success<Integer>) validResult).value());

        var invalidCtx = new ParseContext(sender, List.of("150"), 0);
        var invalidResult = arg.parser().parse(invalidCtx);
        assertInstanceOf(ParseResult.Failure.class, invalidResult);

        var nonNumberCtx = new ParseContext(sender, List.of("abc"), 0);
        var nonNumberResult = arg.parser().parse(nonNumberCtx);
        assertInstanceOf(ParseResult.Failure.class, nonNumberResult);
    }

    @Test
    void shouldParseBigDecimalArgument() {
        var arg = Arguments.bigDecimal("amount", BigDecimal.ZERO, new BigDecimal("1000.50"));
        var sender = mock(CommandSender.class);

        var validCtx = new ParseContext(sender, List.of("123.45"), 0);
        var validResult = arg.parser().parse(validCtx);
        assertInstanceOf(ParseResult.Success.class, validResult);
        assertEquals(new BigDecimal("123.45"), ((ParseResult.Success<BigDecimal>) validResult).value());

        var negativeCtx = new ParseContext(sender, List.of("-1.00"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(negativeCtx));
    }

    @Test
    void shouldParseBooleanArgument() {
        var arg = Arguments.bool("flag");
        var sender = mock(CommandSender.class);

        var trueCtx = new ParseContext(sender, List.of("yes"), 0);
        assertEquals(true, ((ParseResult.Success<Boolean>) arg.parser().parse(trueCtx)).value());

        var falseCtx = new ParseContext(sender, List.of("off"), 0);
        assertEquals(false, ((ParseResult.Success<Boolean>) arg.parser().parse(falseCtx)).value());

        var invalidCtx = new ParseContext(sender, List.of("maybe"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(invalidCtx));
    }

    @Test
    void shouldParseDurationArgument() {
        var arg = Arguments.duration("time");
        var sender = mock(CommandSender.class);

        var ctx = new ParseContext(sender, List.of("30m"), 0);
        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals(Duration.ofMinutes(30), ((ParseResult.Success<Duration>) result).value());
    }

    @Test
    void shouldParseUuidArgument() {
        var arg = Arguments.uuid("id");
        var sender = mock(CommandSender.class);
        var uuid = UUID.randomUUID();

        var ctx = new ParseContext(sender, List.of(uuid.toString()), 0);
        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals(uuid, ((ParseResult.Success<UUID>) result).value());
    }

    @Test
    void shouldParseEnumArgument() {
        var arg = Arguments.enumeration("status", Status.class);
        var sender = mock(CommandSender.class);

        var ctx = new ParseContext(sender, List.of("pending"), 0);
        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals(Status.PENDING, ((ParseResult.Success<Status>) result).value());

        var invalidCtx = new ParseContext(sender, List.of("unknown"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(invalidCtx));
    }

    @Test
    void shouldParseChoiceArgument() {
        var arg = Arguments.choice("mode", "survival", "creative", "adventure");
        var sender = mock(CommandSender.class);

        var ctx = new ParseContext(sender, List.of("creative"), 0);
        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals("creative", ((ParseResult.Success<String>) result).value());
    }

    @Test
    void shouldProvideSuggestions() {
        var arg = Arguments.choice("mode", "survival", "creative", "spectator");
        var sender = mock(CommandSender.class);
        var ctx = new SuggestionContext(sender, List.of("s"), "s");

        var suggestions = arg.suggester().suggest(ctx);
        assertEquals(List.of("survival", "spectator"), suggestions);
    }

    @Test
    void shouldParseOnlinePlayer() {
        var server = mock(org.bukkit.Server.class);
        setBukkitServer(server);

        var player = mock(org.bukkit.entity.Player.class);
        when(player.isOnline()).thenReturn(true);
        when(server.getPlayerExact("Haniel")).thenReturn(player);

        var arg = Arguments.player("target");
        var sender = mock(CommandSender.class);
        var ctx = new ParseContext(sender, List.of("Haniel"), 0);

        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals(player, ((ParseResult.Success<org.bukkit.entity.Player>) result).value());
    }

    @Test
    void shouldFailWhenPlayerIsVanishedFromSender() {
        var server = mock(org.bukkit.Server.class);
        setBukkitServer(server);

        var target = mock(org.bukkit.entity.Player.class);
        when(target.isOnline()).thenReturn(true);
        when(server.getPlayerExact("HiddenPlayer")).thenReturn(target);

        var sender = mock(org.bukkit.entity.Player.class);
        when(sender.canSee(target)).thenReturn(false);

        var arg = Arguments.player("target");
        var ctx = new ParseContext(sender, List.of("HiddenPlayer"), 0);

        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Failure.class, result);
    }

    @Test
    void shouldAllowVanishedPlayerWhenConfigured() {
        var server = mock(org.bukkit.Server.class);
        setBukkitServer(server);

        var target = mock(org.bukkit.entity.Player.class);
        when(target.isOnline()).thenReturn(true);
        when(server.getPlayerExact("HiddenPlayer")).thenReturn(target);

        var sender = mock(org.bukkit.entity.Player.class);
        when(sender.canSee(target)).thenReturn(false);

        var arg = Arguments.player("target", true);
        var ctx = new ParseContext(sender, List.of("HiddenPlayer"), 0);

        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals(target, ((ParseResult.Success<?>) result).value());
    }

    @Test
    void shouldParseOfflinePlayer() {
        var server = mock(org.bukkit.Server.class);
        setBukkitServer(server);

        var offlineTarget = mock(org.bukkit.OfflinePlayer.class);
        when(server.getOfflinePlayer("OfflineUser")).thenReturn(offlineTarget);

        var sender = mock(CommandSender.class);
        var arg = Arguments.offlinePlayer("target");
        var ctx = new ParseContext(sender, List.of("OfflineUser"), 0);

        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Success.class, result);
        assertEquals(offlineTarget, ((ParseResult.Success<?>) result).value());
    }

    @Test
    void shouldParseLongArgumentWithRanges() {
        var arg = Arguments.longNumber("id", 100L, 200L);
        var sender = mock(CommandSender.class);

        var validCtx = new ParseContext(sender, List.of("150"), 0);
        var validResult = arg.parser().parse(validCtx);
        assertInstanceOf(ParseResult.Success.class, validResult);
        assertEquals(150L, ((ParseResult.Success<Long>) validResult).value());

        var outOfRangeCtx = new ParseContext(sender, List.of("250"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(outOfRangeCtx));

        var invalidCtx = new ParseContext(sender, List.of("not-a-number"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(invalidCtx));
    }

    @Test
    void shouldParseDecimalArgumentWithRanges() {
        var arg = Arguments.decimal("price", 10.5, 99.9);
        var sender = mock(CommandSender.class);

        var validCtx = new ParseContext(sender, List.of("50.25"), 0);
        var validResult = arg.parser().parse(validCtx);
        assertInstanceOf(ParseResult.Success.class, validResult);
        assertEquals(50.25, ((ParseResult.Success<Double>) validResult).value());

        var outOfRangeCtx = new ParseContext(sender, List.of("5.0"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(outOfRangeCtx));

        var invalidCtx = new ParseContext(sender, List.of("abc"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(invalidCtx));
    }

    @Test
    void shouldParseVariousDurationUnits() {
        var arg = Arguments.duration("time");
        var sender = mock(CommandSender.class);

        var sCtx = new ParseContext(sender, List.of("10s"), 0);
        assertEquals(
                Duration.ofSeconds(10),
                ((ParseResult.Success<Duration>) arg.parser().parse(sCtx)).value());

        var mCtx = new ParseContext(sender, List.of("5m"), 0);
        assertEquals(
                Duration.ofMinutes(5),
                ((ParseResult.Success<Duration>) arg.parser().parse(mCtx)).value());

        var hCtx = new ParseContext(sender, List.of("2h"), 0);
        assertEquals(
                Duration.ofHours(2),
                ((ParseResult.Success<Duration>) arg.parser().parse(hCtx)).value());

        var dCtx = new ParseContext(sender, List.of("3d"), 0);
        assertEquals(
                Duration.ofDays(3),
                ((ParseResult.Success<Duration>) arg.parser().parse(dCtx)).value());

        var wCtx = new ParseContext(sender, List.of("1w"), 0);
        assertEquals(
                Duration.ofDays(7),
                ((ParseResult.Success<Duration>) arg.parser().parse(wCtx)).value());

        var invalidCtx = new ParseContext(sender, List.of("invalid-duration"), 0);
        assertInstanceOf(ParseResult.Failure.class, arg.parser().parse(invalidCtx));

        var invalidUuidCtx = Arguments.uuid("id").parser().parse(new ParseContext(sender, List.of("not-a-uuid"), 0));
        assertInstanceOf(ParseResult.Failure.class, invalidUuidCtx);
    }

    @Test
    void shouldSupportArgumentWithDefaultAndDescription() {
        var base = Arguments.integer("amount");
        assertFalse(base.isOptional());
        assertTrue(base.defaultValue().isEmpty());
        assertTrue(base.description().isEmpty());

        var withDesc = base.withDescription("Amount of items to give");
        assertTrue(withDesc.description().isPresent());
        assertEquals("Amount of items to give", withDesc.description().get());

        var withDef = withDesc.withDefault(64);
        assertTrue(withDef.isOptional());
        assertTrue(withDef.defaultValue().isPresent());
        assertEquals(64, withDef.defaultValue().get());

        var customSuggester = withDef.withSuggester(ctx -> List.of("16", "32", "64"));
        var suggestions =
                customSuggester.suggester().suggest(new SuggestionContext(mock(CommandSender.class), List.of(), ""));
        assertEquals(List.of("16", "32", "64"), suggestions);
    }

    @Test
    void shouldProvideEnumSuggestions() {
        var arg = Arguments.enumeration("status", Status.class);
        var sender = mock(CommandSender.class);
        var ctx = new SuggestionContext(sender, List.of("p"), "p");

        var suggestions = arg.suggester().suggest(ctx);
        assertEquals(List.of("pending"), suggestions);
    }

    @Test
    void shouldEscapeMiniMessageInjectionInErrorMessages() {
        var arg = Arguments.integer("amount");
        var sender = mock(CommandSender.class);
        var maliciousInput = "<click:run_command:\"/op hacker\">ClickMe</click>";
        var ctx = new ParseContext(sender, List.of(maliciousInput), 0);

        var result = arg.parser().parse(ctx);
        assertInstanceOf(ParseResult.Failure.class, result);
        var failure = (ParseResult.Failure<?>) result;
        // Verify component contains literal escaped text and didn't parse as a clickable component
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(failure.error());
        assertTrue(plain.contains("<click:run_command:\"/op hacker\">ClickMe</click>"));
    }

    @Test
    void shouldCheckParseContextExhaustion() {
        var sender = mock(CommandSender.class);
        var ctxEmpty = new ParseContext(sender, List.of(), 0);
        assertTrue(ctxEmpty.isExhausted());
        assertFalse(ctxEmpty.hasMore());
        assertThrows(IndexOutOfBoundsException.class, ctxEmpty::currentArg);

        var ctxWithArgs = new ParseContext(sender, List.of("arg1"), 0);
        assertFalse(ctxWithArgs.isExhausted());
        assertTrue(ctxWithArgs.hasMore());
        assertEquals("arg1", ctxWithArgs.currentArg());

        var ctxAtEnd = new ParseContext(sender, List.of("arg1"), 1);
        assertTrue(ctxAtEnd.isExhausted());
        assertFalse(ctxAtEnd.hasMore());
        assertThrows(IndexOutOfBoundsException.class, ctxAtEnd::currentArg);
    }

    private static void setBukkitServer(org.bukkit.Server server) {
        try {
            var field = org.bukkit.Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}

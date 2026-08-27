package com.cotani.command.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.command.argument.ParseContext;
import com.cotani.command.argument.ParseResult;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Tag("stress")
class CommandParsingStressTest {
    @Test
    void generatedDurationAndQuotedArgumentsPreserveExactValues() {
        var sender = Mockito.mock(CommandSender.class);
        StressTestSupport.scenarios("command", "parse-arguments", (context, random, player) -> {
            long days = random.nextLong(0, 10_001);
            long hours = random.nextLong(0, 24);
            long minutes = random.nextLong(0, 60);
            String durationInput = days + "d" + hours + "h" + minutes + "m";
            var parsedDuration = DurationParser.parse(durationInput);
            assertEquals(
                    Duration.ofDays(days).plusHours(hours).plusMinutes(minutes), parsedDuration, context::description);

            String value = "player " + context.iteration() + " <red>literal</red>";
            var quoted = QuotedStringReader.read(new ParseContext(sender, List.of('"' + value + '"'), 0));
            var success = org.junit.jupiter.api.Assertions.assertInstanceOf(ParseResult.Success.class, quoted);
            assertEquals(value, success.value(), context::description);
            assertEquals(1, success.consumedArgs(), context::description);

            String invalid = context.iteration() % 2 == 0 ? "-1s" : "1x";
            assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(invalid), context::description);
        });
    }
}

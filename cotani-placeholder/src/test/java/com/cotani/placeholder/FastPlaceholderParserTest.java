package com.cotani.placeholder;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.placeholder.impl.FastPlaceholderParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class FastPlaceholderParserTest {

    @Test
    void testFindTokensBothDelimiters() {
        String input = "Hello {player_name}, you have %coins_balance% coins and {rank:title}!";
        List<FastPlaceholderParser.TokenMatch> tokens = FastPlaceholderParser.findTokens(input);

        assertEquals(3, tokens.size());
        assertEquals("player_name", tokens.get(0).innerToken());
        assertEquals("{player_name}", tokens.get(0).fullToken());
        assertEquals('{', tokens.get(0).delimiter());

        assertEquals("coins_balance", tokens.get(1).innerToken());
        assertEquals("%coins_balance%", tokens.get(1).fullToken());
        assertEquals('%', tokens.get(1).delimiter());

        assertEquals("rank:title", tokens.get(2).innerToken());
        assertEquals("{rank:title}", tokens.get(2).fullToken());
        assertEquals('{', tokens.get(2).delimiter());
    }

    @Test
    void testIgnoreInvalidTokensWithSpaces() {
        String input = "This is not a %valid placeholder with spaces% or { another invalid } one.";
        List<FastPlaceholderParser.TokenMatch> tokens = FastPlaceholderParser.findTokens(input);

        assertTrue(tokens.isEmpty());
    }

    @Test
    void testSplitIdentifierAndParams() {
        String[] underscore = FastPlaceholderParser.splitIdentifierAndParams("player_name");
        assertEquals("player", underscore[0]);
        assertEquals("name", underscore[1]);

        String[] colon = FastPlaceholderParser.splitIdentifierAndParams("economy:wallet_balance");
        assertEquals("economy", colon[0]);
        assertEquals("wallet_balance", colon[1]);

        String[] single = FastPlaceholderParser.splitIdentifierAndParams("ping");
        assertEquals("ping", single[0]);
        assertEquals("", single[1]);
    }

    @Test
    void testReplaceTokens() {
        String input = "Welcome {player_name} to %server_name%!";
        String output = FastPlaceholderParser.replaceTokens(input, token -> {
            if (token.equals("player_name")) return "Haniel";
            if (token.equals("server_name")) return "CotaniNetwork";
            return null;
        });

        assertEquals("Welcome Haniel to CotaniNetwork!", output);
    }
}

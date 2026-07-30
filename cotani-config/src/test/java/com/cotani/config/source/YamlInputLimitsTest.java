package com.cotani.config.source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.config.exception.ConfigException;
import org.junit.jupiter.api.Test;

class YamlInputLimitsTest {

    @Test
    void ignoresStructuralTokensInsideQuotesAndComments() {
        var yaml = "value: \"[[[[{{{{********\" # [[[[{{{{********\nother: '***'";

        assertDoesNotThrow(() -> YamlInputLimits.validate(yaml));
    }

    @Test
    void rejectsTabsUsedForIndentation() {
        assertThrows(ConfigException.class, () -> YamlInputLimits.validate("root:\n\tchild: value"));
    }

    @Test
    void rejectsExcessiveBlockNesting() {
        var yaml = new StringBuilder("root:\n");
        for (int depth = 1; depth <= 65; depth++) {
            yaml.append(" ".repeat(depth)).append("level").append(depth).append(":\n");
        }

        assertThrows(ConfigException.class, () -> YamlInputLimits.validate(yaml.toString()));
    }

    @Test
    void rejectsExcessiveFlowNesting() {
        var yaml = "value: " + "[".repeat(65) + "]".repeat(65);

        assertThrows(ConfigException.class, () -> YamlInputLimits.validate(yaml));
    }

    @Test
    void rejectsExcessiveAliasReferences() {
        var yaml = new StringBuilder("value:");
        for (int alias = 0; alias < 51; alias++) {
            yaml.append(" *anchor");
        }

        assertThrows(ConfigException.class, () -> YamlInputLimits.validate(yaml.toString()));
    }
}

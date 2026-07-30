package com.cotani.config.source;

import com.cotani.config.exception.ConfigException;
import java.util.Objects;

/** Lightweight structural limits applied before handing input to SnakeYAML. */
final class YamlInputLimits {

    private static final int MAXIMUM_NESTING_DEPTH = 64;
    private static final int MAXIMUM_ALIAS_REFERENCES = 50;
    private static final int MAXIMUM_CONTENT_LINES = 50_000;

    private YamlInputLimits() {}

    static void validate(String yaml) {
        Objects.requireNonNull(yaml, "yaml");
        var state = new ValidationState();
        for (String line : yaml.split("\\R", -1)) {
            state.validateLine(line);
        }
    }

    private static boolean isTokenStart(String content, int index) {
        return index == 0
                || Character.isWhitespace(content.charAt(index - 1))
                || "[{,-".indexOf(content.charAt(index - 1)) >= 0;
    }

    private static final class ValidationState {

        private final int[] indentationStack = new int[MAXIMUM_NESTING_DEPTH + 1];
        private int indentationDepth;
        private int flowDepth;
        private int aliases;
        private int contentLines;
        private boolean singleQuoted;
        private boolean doubleQuoted;
        private boolean escaped;

        private void validateLine(String line) {
            int indentation = indentationOf(line);
            String content = line.substring(indentation).stripTrailing();
            if (content.isEmpty() || content.startsWith("#")) {
                return;
            }
            validateContentLineCount();
            updateIndentation(indentation);
            scanContent(content);
        }

        private static int indentationOf(String line) {
            int indentation = 0;
            while (indentation < line.length() && line.charAt(indentation) == ' ') {
                indentation++;
            }
            if (indentation < line.length() && line.charAt(indentation) == '\t') {
                throw new ConfigException("YAML indentation must not contain tabs");
            }
            return indentation;
        }

        private void validateContentLineCount() {
            contentLines++;
            if (contentLines > MAXIMUM_CONTENT_LINES) {
                throw new ConfigException("YAML exceeds maximum content lines " + MAXIMUM_CONTENT_LINES);
            }
        }

        private void updateIndentation(int indentation) {
            while (indentationDepth > 0 && indentation <= indentationStack[indentationDepth]) {
                indentationDepth--;
            }
            if (indentation <= indentationStack[indentationDepth]) {
                return;
            }
            indentationDepth++;
            if (indentationDepth > MAXIMUM_NESTING_DEPTH) {
                throw new ConfigException("YAML exceeds maximum nesting depth " + MAXIMUM_NESTING_DEPTH);
            }
            indentationStack[indentationDepth] = indentation;
        }

        private void scanContent(String content) {
            int index = 0;
            boolean commentStarted = false;
            while (index < content.length() && !commentStarted) {
                commentStarted = scanCharacter(content, index);
                index++;
            }
        }

        private boolean scanCharacter(String content, int index) {
            char current = content.charAt(index);
            if (consumeEscapedCharacter(current)) {
                return false;
            }
            if (!doubleQuoted && current == '\'') {
                singleQuoted = !singleQuoted;
                return false;
            }
            if (!singleQuoted && current == '"') {
                doubleQuoted = !doubleQuoted;
                return false;
            }
            if (singleQuoted || doubleQuoted) {
                return false;
            }
            if (current == '#') {
                return true;
            }
            updateFlowDepth(current);
            countAlias(content, index, current);
            return false;
        }

        private boolean consumeEscapedCharacter(char current) {
            if (!doubleQuoted) {
                return false;
            }
            if (escaped) {
                escaped = false;
                return true;
            }
            if (current == '\\') {
                escaped = true;
                return true;
            }
            return false;
        }

        private void updateFlowDepth(char current) {
            if (current == '[' || current == '{') {
                flowDepth++;
                if (flowDepth > MAXIMUM_NESTING_DEPTH) {
                    throw new ConfigException("YAML exceeds maximum flow nesting depth " + MAXIMUM_NESTING_DEPTH);
                }
            } else if (current == ']' || current == '}') {
                flowDepth = Math.max(0, flowDepth - 1);
            }
        }

        private void countAlias(String content, int index, char current) {
            if (current == '*' && isTokenStart(content, index) && ++aliases > MAXIMUM_ALIAS_REFERENCES) {
                throw new ConfigException("YAML exceeds maximum alias references " + MAXIMUM_ALIAS_REFERENCES);
            }
        }
    }
}

package com.cotani.config.source;

import com.cotani.config.exception.ConfigException;

/** Lightweight structural limits applied before handing input to SnakeYAML. */
final class YamlInputLimits {

    private static final int MAXIMUM_NESTING_DEPTH = 64;
    private static final int MAXIMUM_ALIAS_REFERENCES = 50;
    private static final int MAXIMUM_CONTENT_LINES = 50_000;

    private YamlInputLimits() {}

    static void validate(String yaml) {
        int[] indentationStack = new int[MAXIMUM_NESTING_DEPTH + 1];
        int indentationDepth = 0;
        int flowDepth = 0;
        int aliases = 0;
        int contentLines = 0;
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;

        for (String line : yaml.split("\\R", -1)) {
            int indentation = 0;
            while (indentation < line.length()) {
                char current = line.charAt(indentation);
                if (current == ' ') {
                    indentation++;
                    continue;
                }
                if (current == '\t') {
                    throw new ConfigException("YAML indentation must not contain tabs");
                }
                break;
            }
            String content = line.substring(indentation).stripTrailing();
            if (content.isEmpty() || content.startsWith("#")) {
                continue;
            }
            if (++contentLines > MAXIMUM_CONTENT_LINES) {
                throw new ConfigException("YAML exceeds maximum content lines " + MAXIMUM_CONTENT_LINES);
            }

            while (indentationDepth > 0 && indentation <= indentationStack[indentationDepth]) {
                indentationDepth--;
            }
            if (indentation > indentationStack[indentationDepth]) {
                indentationDepth++;
                if (indentationDepth > MAXIMUM_NESTING_DEPTH) {
                    throw new ConfigException("YAML exceeds maximum nesting depth " + MAXIMUM_NESTING_DEPTH);
                }
                indentationStack[indentationDepth] = indentation;
            }

            for (int index = 0; index < content.length(); index++) {
                char current = content.charAt(index);
                if (doubleQuoted && escaped) {
                    escaped = false;
                    continue;
                }
                if (doubleQuoted && current == '\\') {
                    escaped = true;
                    continue;
                }
                if (!doubleQuoted && current == '\'') {
                    singleQuoted = !singleQuoted;
                    continue;
                }
                if (!singleQuoted && current == '"') {
                    doubleQuoted = !doubleQuoted;
                    continue;
                }
                if (singleQuoted || doubleQuoted || current == '#') {
                    if (current == '#') {
                        break;
                    }
                    continue;
                }
                if (current == '[' || current == '{') {
                    if (++flowDepth > MAXIMUM_NESTING_DEPTH) {
                        throw new ConfigException("YAML exceeds maximum flow nesting depth " + MAXIMUM_NESTING_DEPTH);
                    }
                } else if (current == ']' || current == '}') {
                    flowDepth = Math.max(0, flowDepth - 1);
                } else if (current == '*' && isTokenStart(content, index)) {
                    if (++aliases > MAXIMUM_ALIAS_REFERENCES) {
                        throw new ConfigException("YAML exceeds maximum alias references " + MAXIMUM_ALIAS_REFERENCES);
                    }
                }
            }
        }
    }

    private static boolean isTokenStart(String content, int index) {
        return index == 0
                || Character.isWhitespace(content.charAt(index - 1))
                || "[{,-".indexOf(content.charAt(index - 1)) >= 0;
    }
}

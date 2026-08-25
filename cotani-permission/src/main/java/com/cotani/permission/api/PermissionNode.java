package com.cotani.permission.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, normalized permission node.
 *
 * <p>Nodes use dot-separated segments. A trailing {@code *} segment is a wildcard, for example
 * {@code cotani.command.*}. The standalone node {@code *} matches every permission.
 */
public record PermissionNode(String value) {
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_-]+");

    public PermissionNode {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Permission node must not be empty");
        }

        var segments = value.split("\\.", -1);
        for (int index = 0; index < segments.length; index++) {
            var segment = segments[index];
            if ("*".equals(segment)) {
                if (index != segments.length - 1) {
                    throw new IllegalArgumentException("Wildcard must be the final permission segment");
                }
                continue;
            }
            if (!SEGMENT.matcher(segment).matches()) {
                throw new IllegalArgumentException("Invalid permission segment: " + segment);
            }
        }
    }

    public static PermissionNode of(String value) {
        return new PermissionNode(value);
    }

    /** Returns whether this node can provide a value for the requested node. */
    public boolean matches(PermissionNode requested) {
        Objects.requireNonNull(requested, "requested");

        var granted = segments();
        var required = requested.segments();
        if (granted.length > required.length) {
            return false;
        }

        for (int index = 0; index < granted.length; index++) {
            var segment = granted[index];
            if ("*".equals(segment)) {
                return index < required.length;
            }
            if (!segment.equals(required[index])) {
                return false;
            }
        }

        return granted.length == required.length;
    }

    /** Returns a specificity score where exact nodes outrank wildcard nodes. */
    public int specificity() {
        return (int) Arrays.stream(segments())
                .filter(segment -> !"*".equals(segment))
                .count();
    }

    private String[] segments() {
        return value.split("\\.", -1);
    }
}

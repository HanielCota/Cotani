package com.cotani.command.argument;

import com.cotani.command.internal.DurationParser;
import com.cotani.command.internal.QuotedStringReader;
import com.cotani.text.MiniMessages;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Standard factory methods for building typed command {@link Argument} instances.
 */
public final class Arguments {
    private static final String CLOSE_TAGS = "</yellow></red>";

    private Arguments() {}

    private static String missingArg(String prefix, String name) {
        return "<red>" + prefix + ": <yellow>" + name + CLOSE_TAGS;
    }

    /**
     * Unquoted single-word string argument.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<String> string(String name) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing required argument", name));
                    }
                    return ParseResult.success(context.currentArg(), 1);
                },
                SuggestionProvider.empty());
    }

    /**
     * String argument supporting quotation marks (e.g. {@code "multi word text"}).
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<String> quotedString(String name) {
        Objects.requireNonNull(name, "name");
        return Argument.of(name, QuotedStringReader::read, SuggestionProvider.empty());
    }

    /**
     * Greedy string argument that consumes all remaining command tokens.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<String> greedyString(String name) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing required text for", name));
                    }
                    var remaining = context.remainingArgs();
                    return ParseResult.success(String.join(" ", remaining), remaining.size());
                },
                SuggestionProvider.empty());
    }

    /**
     * Integer argument without bounds.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<Integer> integer(String name) {
        return integer(name, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Integer argument bounded between {@code min} and {@code max} inclusive.
     *
     * @param name argument name
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return argument definition
     */
    public static Argument<Integer> integer(String name, int min, int max) {
        Objects.requireNonNull(name, "name");
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") cannot be greater than max (" + max + ")");
        }
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing integer argument", name));
                    }
                    var raw = context.currentArg();
                    try {
                        int value = Integer.parseInt(raw);
                        if (value < min || value > max) {
                            return ParseResult.failure("<red>Argument <yellow>" + name + "</yellow> must be between "
                                    + min + " and " + max + " (got " + value + ").</red>");
                        }
                        return ParseResult.success(value, 1);
                    } catch (NumberFormatException ex) {
                        return ParseResult.failure("<red>Invalid integer '<yellow>" + MiniMessages.escape(raw)
                                + "</yellow>' for argument <yellow>" + name + "</yellow>.</red>");
                    }
                },
                SuggestionProvider.empty());
    }

    /**
     * 64-bit Long argument without bounds.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<Long> longNumber(String name) {
        return longNumber(name, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /**
     * 64-bit Long argument bounded between {@code min} and {@code max} inclusive.
     *
     * @param name argument name
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return argument definition
     */
    public static Argument<Long> longNumber(String name, long min, long max) {
        Objects.requireNonNull(name, "name");
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing number argument", name));
                    }
                    var raw = context.currentArg();
                    try {
                        long value = Long.parseLong(raw);
                        if (value < min || value > max) {
                            return ParseResult.failure("<red>Argument <yellow>" + name + "</yellow> must be between "
                                    + min + " and " + max + ".</red>");
                        }
                        return ParseResult.success(value, 1);
                    } catch (NumberFormatException ex) {
                        return ParseResult.failure("<red>Invalid number '<yellow>" + MiniMessages.escape(raw)
                                + "</yellow>' for argument <yellow>" + name + "</yellow>.</red>");
                    }
                },
                SuggestionProvider.empty());
    }

    /**
     * Double argument without bounds.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<Double> decimal(String name) {
        return decimal(name, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /**
     * Double argument bounded between {@code min} and {@code max} inclusive.
     *
     * @param name argument name
     * @param min minimum allowed value
     * @param max maximum allowed value
     * @return argument definition
     */
    public static Argument<Double> decimal(String name, double min, double max) {
        Objects.requireNonNull(name, "name");
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing decimal argument", name));
                    }
                    var raw = context.currentArg();
                    try {
                        double value = Double.parseDouble(raw);
                        if (Double.isNaN(value) || Double.isInfinite(value)) {
                            return ParseResult.failure(
                                    "<red>Invalid decimal value for argument <yellow>" + name + "</yellow>.</red>");
                        }
                        if (value < min || value > max) {
                            return ParseResult.failure("<red>Argument <yellow>" + name + "</yellow> must be between "
                                    + min + " and " + max + ".</red>");
                        }
                        return ParseResult.success(value, 1);
                    } catch (NumberFormatException ex) {
                        return ParseResult.failure("<red>Invalid decimal '<yellow>" + MiniMessages.escape(raw)
                                + "</yellow>' for argument <yellow>" + name + "</yellow>.</red>");
                    }
                },
                SuggestionProvider.empty());
    }

    /**
     * Exact {@link BigDecimal} argument without bounds.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<BigDecimal> bigDecimal(String name) {
        return bigDecimal(name, null, null);
    }

    /**
     * Exact {@link BigDecimal} argument bounded by optional minimum and maximum.
     *
     * @param name argument name
     * @param min minimum allowed value, or null for unbounded
     * @param max maximum allowed value, or null for unbounded
     * @return argument definition
     */
    public static Argument<BigDecimal> bigDecimal(String name, @Nullable BigDecimal min, @Nullable BigDecimal max) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing amount argument", name));
                    }
                    var raw = context.currentArg();
                    try {
                        var value = new BigDecimal(raw);
                        if (min != null && value.compareTo(min) < 0) {
                            return ParseResult.failure("<red>Argument <yellow>" + name + "</yellow> must be at least "
                                    + min.toPlainString() + ".</red>");
                        }
                        if (max != null && value.compareTo(max) > 0) {
                            return ParseResult.failure("<red>Argument <yellow>" + name + "</yellow> must not exceed "
                                    + max.toPlainString() + ".</red>");
                        }
                        return ParseResult.success(value, 1);
                    } catch (NumberFormatException ex) {
                        return ParseResult.failure("<red>Invalid amount '<yellow>" + MiniMessages.escape(raw)
                                + "</yellow>' for argument <yellow>" + name + "</yellow>.</red>");
                    }
                },
                SuggestionProvider.empty());
    }

    /**
     * Boolean argument accepting true/false, yes/no, on/off, 1/0.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<Boolean> bool(String name) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing boolean argument", name));
                    }
                    var raw = context.currentArg().toLowerCase(Locale.ROOT);
                    return switch (raw) {
                        case "true", "yes", "on", "1", "t", "y" -> ParseResult.success(Boolean.TRUE, 1);
                        case "false", "no", "off", "0", "f", "n" -> ParseResult.success(Boolean.FALSE, 1);
                        default ->
                            ParseResult.failure("<red>Invalid boolean '<yellow>" + MiniMessages.escape(raw)
                                    + "</yellow>' (expected true/false, yes/no).</red>");
                    };
                },
                SuggestionProvider.of("true", "false"));
    }

    /**
     * Human-readable {@link Duration} argument (e.g. {@code "10s"}, {@code "5m"}, {@code "2h"}, {@code "1d"}).
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<Duration> duration(String name) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing duration argument", name));
                    }
                    var raw = context.currentArg();
                    try {
                        return ParseResult.success(DurationParser.parse(raw), 1);
                    } catch (IllegalArgumentException | ArithmeticException _) {
                        return ParseResult.failure("<red>Invalid duration format '<yellow>" + MiniMessages.escape(raw)
                                + "</yellow>' (e.g. 10s, 5m, 1h, 1d).</red>");
                    }
                },
                SuggestionProvider.of("10s", "1m", "5m", "30m", "1h", "1d", "7d"));
    }

    /**
     * UUID argument.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<UUID> uuid(String name) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing UUID argument", name));
                    }
                    var raw = context.currentArg();
                    try {
                        return ParseResult.success(UUID.fromString(raw), 1);
                    } catch (IllegalArgumentException ex) {
                        return ParseResult.failure(
                                "<red>Invalid UUID '<yellow>" + MiniMessages.escape(raw) + "</yellow>'.</red>");
                    }
                },
                SuggestionProvider.empty());
    }

    /**
     * Online {@link Player} argument resolved by username.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<Player> player(String name) {
        return player(name, false);
    }

    /**
     * Online {@link Player} argument resolved by username with optional vanish bypass.
     *
     * @param name argument name
     * @param allowVanished whether to allow matching players hidden from the sender (e.g. vanished admins)
     * @return argument definition
     */
    public static Argument<Player> player(String name, boolean allowVanished) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing player name for argument", name));
                    }
                    var raw = context.currentArg();
                    var server = Bukkit.getServer();
                    if (server == null) {
                        return ParseResult.failure("<red>Server is currently unavailable.</red>");
                    }
                    var target = server.getPlayerExact(raw);
                    if (target == null || !target.isOnline()) {
                        return ParseResult.failure(
                                "<red>Player '<yellow>" + MiniMessages.escape(raw) + "</yellow>' is not online.</red>");
                    }
                    if (!allowVanished
                            && context.sender() instanceof Player senderPlayer
                            && !senderPlayer.canSee(target)) {
                        return ParseResult.failure(
                                "<red>Player '<yellow>" + MiniMessages.escape(raw) + "</yellow>' is not online.</red>");
                    }
                    return ParseResult.success(target, 1);
                },
                SuggestionProvider.players());
    }

    /**
     * {@link org.bukkit.OfflinePlayer} argument resolved by username or UUID.
     *
     * @param name argument name
     * @return argument definition
     */
    public static Argument<org.bukkit.OfflinePlayer> offlinePlayer(String name) {
        Objects.requireNonNull(name, "name");
        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing player name or UUID for argument", name));
                    }
                    var raw = context.currentArg();
                    var server = Bukkit.getServer();
                    if (server == null) {
                        return ParseResult.failure("<red>Server is currently unavailable.</red>");
                    }
                    var online = server.getPlayerExact(raw);
                    if (online != null) {
                        return ParseResult.success(online, 1);
                    }
                    try {
                        var uuid = UUID.fromString(raw);
                        return ParseResult.success(server.getOfflinePlayer(uuid), 1);
                    } catch (IllegalArgumentException _) {
                        return ParseResult.success(server.getOfflinePlayer(raw), 1);
                    }
                },
                SuggestionProvider.players());
    }

    /**
     * Enum argument parsing matching constant names (case-insensitive).
     *
     * @param name argument name
     * @param enumClass enum class
     * @param <E> enum type
     * @return argument definition
     */
    public static <E extends Enum<E>> Argument<E> enumeration(String name, Class<E> enumClass) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(enumClass, "enumClass");
        var constants = enumClass.getEnumConstants();
        if (constants == null) {
            throw new IllegalArgumentException("Class " + enumClass.getName() + " does not contain enum constants");
        }
        var names = Arrays.stream(constants)
                .map(e -> e.name().toLowerCase(Locale.ROOT))
                .toList();

        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing option for", name));
                    }
                    var raw = context.currentArg();
                    for (var constant : constants) {
                        if (constant.name().equalsIgnoreCase(raw)) {
                            return ParseResult.success(constant, 1);
                        }
                    }
                    return ParseResult.failure("<red>Invalid choice '<yellow>" + MiniMessages.escape(raw)
                            + "</yellow>'. Allowed: <yellow>" + String.join(", ", names) + CLOSE_TAGS);
                },
                SuggestionProvider.of(names));
    }

    /**
     * Choice argument matching one of the provided string choices (case-insensitive).
     *
     * @param name argument name
     * @param choices allowed values
     * @return argument definition
     */
    public static Argument<String> choice(String name, Collection<String> choices) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(choices, "choices");
        var copy = List.copyOf(choices);

        return Argument.of(
                name,
                context -> {
                    if (context.isExhausted()) {
                        return ParseResult.failure(missingArg("Missing choice for", name));
                    }
                    var raw = context.currentArg();
                    for (var option : copy) {
                        if (option.equalsIgnoreCase(raw)) {
                            return ParseResult.success(option, 1);
                        }
                    }
                    return ParseResult.failure("<red>Invalid choice '<yellow>" + MiniMessages.escape(raw)
                            + "</yellow>'. Allowed: <yellow>" + String.join(", ", copy) + CLOSE_TAGS);
                },
                SuggestionProvider.of(copy));
    }

    /**
     * Choice argument matching one of the provided vararg string choices.
     *
     * @param name argument name
     * @param choices allowed values
     * @return argument definition
     */
    public static Argument<String> choice(String name, String... choices) {
        Objects.requireNonNull(choices, "choices");
        return choice(name, List.of(choices));
    }

    /**
     * Custom argument with provided parser and suggester.
     *
     * @param name argument name
     * @param parser argument parser
     * @param suggester suggestion provider
     * @param <T> value type
     * @return argument definition
     */
    public static <T> Argument<T> custom(String name, ArgumentParser<T> parser, SuggestionProvider suggester) {
        return Argument.of(name, parser, suggester);
    }
}

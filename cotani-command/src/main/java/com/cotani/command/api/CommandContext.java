package com.cotani.command.api;

import com.cotani.command.argument.Argument;
import com.cotani.command.argument.PlayerRef;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Execution context provided when a command or subcommand is dispatched.
 */
public interface CommandContext {
    /**
     * Returns the command sender (player, console, block, etc.).
     *
     * <p>The returned sender is a live Bukkit/Paper object. Touching it is only safe on the thread
     * that owns it: the main thread for {@code executes} handlers, the sender's entity thread for
     * {@code executesEntity} handlers. Inside {@code executesAsync} handlers use {@link #playerId()}
     * and reply methods instead of accessing the sender directly.
     *
     * @return the sender
     */
    CommandSender sender();

    /**
     * Returns the immutable identity of the sender when the sender is an in-game player.
     *
     * <p>This is the preferred way to identify the sender from any thread: the returned id may be
     * captured across async boundaries, while live {@link Player} access must remain on the owning
     * thread.
     *
     * @return the player id, or empty when the sender is not an in-game player
     */
    Optional<UUID> playerId();

    /**
     * Returns the sender as a {@link Player} if the sender is an in-game player.
     *
     * <p>The returned player is a live Bukkit/Paper object. Only touch it on the thread that owns
     * the sender ({@code executes}/{@code executesEntity}); in async handlers prefer
     * {@link #playerId()}.
     *
     * @return player or empty
     */
    Optional<Player> player();

    /**
     * Checks if the sender is an in-game {@link Player}.
     *
     * @return {@code true} if player
     */
    default boolean isPlayer() {
        return player().isPresent();
    }

    /**
     * Checks if the sender is the console or a remote console.
     *
     * @return {@code true} if console
     */
    default boolean isConsole() {
        var s = sender();
        return s instanceof org.bukkit.command.ConsoleCommandSender
                || s instanceof org.bukkit.command.RemoteConsoleCommandSender;
    }

    /**
     * Returns the sender as a {@link Player} or throws {@link CommandExecutionException} if not a player.
     *
     * <p>Only call this from handlers that run on the sender's owning thread ({@code executes} or
     * {@code executesEntity}). In {@code executesAsync} handlers capture {@link #playerId()} instead
     * and re-resolve the player through the scheduler before touching it.
     *
     * @return the player sender
     * @throws CommandExecutionException if sender is not a player
     */
    Player requirePlayer();

    /**
     * Returns the parsed value for the given argument.
     *
     * @param argument the argument definition
     * @param <T> value type
     * @return parsed argument value
     * @throws IllegalArgumentException if the argument was not parsed or is missing
     */
    <T> T get(Argument<T> argument);

    /**
     * Returns the parsed value for the argument with the given name.
     *
     * @param name argument name
     * @param type value type class
     * @param <T> value type
     * @return parsed argument value
     * @throws IllegalArgumentException if the argument was not parsed or is missing
     */
    <T> T get(String name, Class<T> type);

    /**
     * Returns the optional parsed value for the given argument.
     *
     * @param argument the argument definition
     * @param <T> value type
     * @return optional parsed value
     */
    <T> Optional<T> getOptional(Argument<T> argument);

    /**
     * Returns the optional parsed value for the argument with the given name.
     *
     * @param name argument name
     * @param type value type class
     * @param <T> value type
     * @return optional parsed value
     */
    <T> Optional<T> getOptional(String name, Class<T> type);

    /**
     * Checks if a parsed value exists for the given argument name.
     *
     * @param name argument name
     * @return {@code true} if present
     */
    boolean has(String name);

    /**
     * Returns an unmodifiable list of the raw string arguments supplied by the sender.
     *
     * @return raw argument list
     */
    List<String> rawArgs();

    /**
     * Returns the command alias or name used to invoke the command.
     *
     * @return matched command alias
     */
    String matchedAlias();

    /**
     * Returns the active {@link PaperTaskScheduler} instance.
     *
     * @return task scheduler
     */
    PaperTaskScheduler scheduler();

    /**
     * Returns the string value for the given argument name.
     *
     * @param name argument name
     * @return parsed string
     */
    default String getString(String name) {
        return get(name, String.class);
    }

    /**
     * Returns the integer value for the given argument name.
     *
     * @param name argument name
     * @return parsed integer
     */
    default int getInt(String name) {
        return get(name, Integer.class);
    }

    /**
     * Returns the long value for the given argument name.
     *
     * @param name argument name
     * @return parsed long
     */
    default long getLong(String name) {
        return get(name, Long.class);
    }

    /**
     * Returns the double value for the given argument name.
     *
     * @param name argument name
     * @return parsed double
     */
    default double getDouble(String name) {
        return get(name, Double.class);
    }

    /**
     * Returns the boolean value for the given argument name.
     *
     * @param name argument name
     * @return parsed boolean
     */
    default boolean getBoolean(String name) {
        return get(name, Boolean.class);
    }

    /**
     * Returns the {@link java.math.BigDecimal} value for the given argument name.
     *
     * @param name argument name
     * @return parsed BigDecimal
     */
    default java.math.BigDecimal getBigDecimal(String name) {
        return get(name, java.math.BigDecimal.class);
    }

    /**
     * Returns the {@link java.time.Duration} value for the given argument name.
     *
     * @param name argument name
     * @return parsed duration
     */
    default java.time.Duration getDuration(String name) {
        return get(name, java.time.Duration.class);
    }

    /**
     * Returns the {@link java.util.UUID} value for the given argument name.
     *
     * @param name argument name
     * @return parsed UUID
     */
    default java.util.UUID getUUID(String name) {
        return get(name, java.util.UUID.class);
    }

    /**
     * Returns the immutable {@link PlayerRef} value parsed for the given argument name.
     *
     * <p>Arguments created by {@code Arguments.player(String)} store this reference instead of a
     * live player, making it safe to read inside {@code executesAsync} handlers. Use the scheduler
     * ({@link #scheduler()}) entity transitions when the live player must be touched.
     *
     * @param name argument name
     * @return parsed player reference
     */
    default PlayerRef getPlayerRef(String name) {
        return get(name, PlayerRef.class);
    }

    /**
     * Returns the Enum constant value for the given argument name.
     *
     * @param name argument name
     * @param enumClass enum class
     * @param <E> enum type
     * @return parsed enum constant
     */
    default <E extends Enum<E>> E getEnum(String name, Class<E> enumClass) {
        return get(name, enumClass);
    }

    /**
     * Sends an Adventure {@link Component} message to the sender.
     *
     * <p>This method is safe to call from any thread, including inside async continuations:
     * delivery is routed through the scheduler to the thread that owns the sender.
     *
     * @param component message component
     */
    void reply(Component component);

    /**
     * Sends a MiniMessage-formatted message to the sender.
     *
     * @param miniMessage MiniMessage template
     */
    void reply(String miniMessage);

    /**
     * Sends a MiniMessage-formatted message with custom tag resolvers to the sender.
     *
     * @param miniMessage MiniMessage template
     * @param resolvers tag resolvers
     */
    void reply(String miniMessage, TagResolver... resolvers);

    /**
     * Sends an error MiniMessage-formatted message to the sender.
     *
     * @param miniMessage error message template
     */
    void replyError(String miniMessage);

    /**
     * Sends an error MiniMessage-formatted message with custom tag resolvers to the sender.
     *
     * @param miniMessage error message template
     * @param resolvers tag resolvers
     */
    default void replyError(String miniMessage, TagResolver... resolvers) {
        reply("<red>" + miniMessage + "</red>", resolvers);
    }

    /**
     * Sends a success MiniMessage-formatted message to the sender.
     *
     * @param miniMessage success message template
     */
    default void replySuccess(String miniMessage) {
        reply("<green>" + miniMessage + "</green>");
    }

    /**
     * Sends a success MiniMessage-formatted message with custom tag resolvers to the sender.
     *
     * @param miniMessage success message template
     * @param resolvers tag resolvers
     */
    default void replySuccess(String miniMessage, TagResolver... resolvers) {
        reply("<green>" + miniMessage + "</green>", resolvers);
    }

    /**
     * Sends an informational MiniMessage-formatted message to the sender.
     *
     * @param miniMessage info message template
     */
    default void replyInfo(String miniMessage) {
        reply("<yellow>" + miniMessage + "</yellow>");
    }

    /**
     * Sends an informational MiniMessage-formatted message with custom tag resolvers to the sender.
     *
     * @param miniMessage info message template
     * @param resolvers tag resolvers
     */
    default void replyInfo(String miniMessage, TagResolver... resolvers) {
        reply("<yellow>" + miniMessage + "</yellow>", resolvers);
    }
}

package com.cotani.command.internal;

import com.cotani.api.InternalApi;
import com.cotani.command.api.CommandContext;
import com.cotani.command.api.CommandExecutionException;
import com.cotani.command.argument.Argument;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.text.MiniMessages;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Default implementation of {@link CommandContext}.
 */
@InternalApi
public final class DefaultCommandContext implements CommandContext {
    private final CommandSender sender;
    private final Map<String, Object> parsedValues;
    private final List<String> rawArgs;
    private final String matchedAlias;
    private final PaperTaskScheduler scheduler;

    public DefaultCommandContext(
            CommandSender sender,
            Map<String, Object> parsedValues,
            List<String> rawArgs,
            String matchedAlias,
            PaperTaskScheduler scheduler) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.parsedValues = Map.copyOf(Objects.requireNonNull(parsedValues, "parsedValues"));
        this.rawArgs = List.copyOf(Objects.requireNonNull(rawArgs, "rawArgs"));
        this.matchedAlias = Objects.requireNonNull(matchedAlias, "matchedAlias");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CommandSender sender() {
        return sender;
    }

    @Override
    public Optional<UUID> playerId() {
        return sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
    }

    @Override
    public Optional<Player> player() {
        return sender instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    @Override
    public Player requirePlayer() {
        if (sender instanceof Player player) {
            return player;
        }
        throw new CommandExecutionException(
                MiniMessages.parse("<red>This command can only be executed by players.</red>"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Argument<T> argument) {
        Objects.requireNonNull(argument, "argument");
        return (T) get(argument.name(), Object.class);
    }

    @Override
    public <T> T get(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        var val = parsedValues.get(name);
        if (val == null) {
            throw new IllegalArgumentException("No parsed value found for argument '" + name + "'");
        }
        if (!type.isInstance(val)) {
            throw new IllegalArgumentException(
                    "Argument '" + name + "' is of type " + val.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(val);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOptional(Argument<T> argument) {
        Objects.requireNonNull(argument, "argument");
        return (Optional<T>) getOptional(argument.name(), Object.class);
    }

    @Override
    public <T> Optional<T> getOptional(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        var val = parsedValues.get(name);
        if (!type.isInstance(val)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(val));
    }

    @Override
    public boolean has(String name) {
        Objects.requireNonNull(name, "name");
        return parsedValues.containsKey(name);
    }

    @Override
    public List<String> rawArgs() {
        return rawArgs;
    }

    @Override
    public String matchedAlias() {
        return matchedAlias;
    }

    private static final String MINI_MESSAGE_PARAM = "miniMessage";

    @Override
    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void reply(Component component) {
        Objects.requireNonNull(component, "component");
        deliver(component);
    }

    /**
     * Delivers a message to the sender from any thread.
     *
     * <p>Player senders are resolved on their owning entity thread; other senders are messaged from
     * the global scheduler, so {@code reply} is safe to call from async handlers and continuations.
     */
    private void deliver(Component component) {
        if (sender instanceof Player player) {
            var playerId = player.getUniqueId();
            scheduler.entity(playerId, () -> {
                var current = Bukkit.getPlayer(playerId);
                if (current != null && current.isOnline()) {
                    current.sendMessage(component);
                }
            });
            return;
        }
        scheduler.global(() -> sender.sendMessage(component));
    }

    @Override
    public void reply(String miniMessage) {
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_PARAM);
        reply(MiniMessages.parse(miniMessage));
    }

    @Override
    public void reply(String miniMessage, TagResolver... resolvers) {
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_PARAM);
        Objects.requireNonNull(resolvers, "resolvers");
        reply(MiniMessages.parse(miniMessage, resolvers));
    }

    @Override
    public void replyError(String miniMessage) {
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_PARAM);
        reply(MiniMessages.parse("<red>" + miniMessage + "</red>"));
    }

    @Override
    public void replyError(String miniMessage, TagResolver... resolvers) {
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_PARAM);
        Objects.requireNonNull(resolvers, "resolvers");
        reply(MiniMessages.parse("<red>" + miniMessage + "</red>", resolvers));
    }

    @Override
    public void replySuccess(String miniMessage, TagResolver... resolvers) {
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_PARAM);
        Objects.requireNonNull(resolvers, "resolvers");
        reply(MiniMessages.parse("<green>" + miniMessage + "</green>", resolvers));
    }

    @Override
    public void replyInfo(String miniMessage, TagResolver... resolvers) {
        Objects.requireNonNull(miniMessage, MINI_MESSAGE_PARAM);
        Objects.requireNonNull(resolvers, "resolvers");
        reply(MiniMessages.parse("<yellow>" + miniMessage + "</yellow>", resolvers));
    }
}

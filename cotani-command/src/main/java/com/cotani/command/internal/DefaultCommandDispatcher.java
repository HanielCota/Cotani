package com.cotani.command.internal;

import com.cotani.api.InternalApi;
import com.cotani.command.api.CommandContext;
import com.cotani.command.api.CommandExecutionException;
import com.cotani.command.api.CommandExecutionMode;
import com.cotani.command.api.CommandNode;
import com.cotani.command.argument.Argument;
import com.cotani.command.argument.ParseContext;
import com.cotani.command.argument.ParseResult;
import com.cotani.command.argument.SuggestionContext;
import com.cotani.command.feedback.CommandFeedback;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Internal command dispatcher responsible for tree resolution, permissions, cooldowns, argument parsing,
 * thread-safe scheduling, and tab completion.
 */
@InternalApi
public final class DefaultCommandDispatcher {
    private final Plugin plugin;
    private final PaperTaskScheduler scheduler;
    private final CommandFeedback feedback;

    public DefaultCommandDispatcher(Plugin plugin, PaperTaskScheduler scheduler, CommandFeedback feedback) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    public void dispatch(CommandNode rootNode, CommandSender sender, String alias, List<String> rawArgs) {
        Objects.requireNonNull(rootNode, "rootNode");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(rawArgs, "rawArgs");

        var path = new ArrayList<String>();
        path.add(alias);

        var traversal = traverseSubcommands(rootNode, rawArgs, path);
        var currentNode = traversal.node();
        var argIndex = traversal.argIndex();

        if (!validateGuards(currentNode, sender, path, rawArgs, argIndex)) {
            return;
        }

        var remainingTokens = rawArgs.subList(argIndex, rawArgs.size());
        var parsedValues = parseArguments(currentNode, sender, remainingTokens, path);
        if (parsedValues == null) {
            return;
        }

        var cooldownRemaining =
                currentNode.cooldown().tryAcquire(sender, currentNode.name()).orElse(null);
        if (cooldownRemaining != null) {
            sender.sendMessage(feedback.formatCooldownActive(cooldownRemaining));
            return;
        }

        var context = new DefaultCommandContext(sender, parsedValues, rawArgs, alias, scheduler);
        executeNode(currentNode, currentNode.executionMode(), context, sender);
    }

    private TraversalResult traverseSubcommands(CommandNode rootNode, List<String> rawArgs, List<String> path) {
        var currentNode = rootNode;
        var argIndex = 0;
        while (argIndex < rawArgs.size()) {
            var token = rawArgs.get(argIndex);
            var nextSubcommand = currentNode.findSubcommand(token).orElse(null);
            if (nextSubcommand == null) {
                break;
            }
            currentNode = nextSubcommand;
            path.add(token);
            argIndex++;
        }
        return new TraversalResult(currentNode, argIndex);
    }

    private boolean validateGuards(
            CommandNode node, CommandSender sender, List<String> path, List<String> rawArgs, int argIndex) {
        if (argIndex < rawArgs.size()
                && node.hasSubcommands()
                && node.arguments().isEmpty()
                && !node.canExecute()) {
            var unknownToken = rawArgs.get(argIndex);
            var usage = resolveUsage(path, node);
            sender.sendMessage(feedback.formatUnknownSubcommand(unknownToken, usage));
            return false;
        }

        if (!node.permission().test(sender)) {
            sender.sendMessage(feedback.formatPermissionDenied(node.permission().node()));
            return false;
        }

        if (!node.senderType().test(sender)) {
            switch (node.senderType()) {
                case PLAYER -> sender.sendMessage(feedback.formatPlayerOnly());
                case CONSOLE -> sender.sendMessage(feedback.formatConsoleOnly());
                case ANY -> {
                    // Sender requirement ANY permits all senders
                }
            }
            return false;
        }

        if (!node.canExecute()) {
            var usage = resolveUsage(path, node);
            sender.sendMessage(feedback.formatInvalidUsage(usage));
            return false;
        }

        return true;
    }

    private @Nullable Map<String, Object> parseArguments(
            CommandNode node, CommandSender sender, List<String> remainingTokens, List<String> path) {
        var parsedValues = new HashMap<String, Object>();
        var tokenPointer = 0;

        for (Argument<?> argument : node.arguments()) {
            if (tokenPointer >= remainingTokens.size()) {
                if (argument.isOptional()) {
                    argument.defaultValue().ifPresent(def -> parsedValues.put(argument.name(), def));
                    continue;
                }
                sender.sendMessage(feedback.formatInvalidUsage(resolveUsage(path, node)));
                return null;
            }

            var parseContext = new ParseContext(sender, remainingTokens, tokenPointer);
            var result = argument.parser().parse(parseContext);

            if (result instanceof ParseResult.Failure(Component error)) {
                sender.sendMessage(error);
                return null;
            }
            if (result instanceof ParseResult.Success(Object val, int consumedArgs)) {
                parsedValues.put(argument.name(), val);
                tokenPointer += consumedArgs;
            }
        }

        if (tokenPointer < remainingTokens.size()) {
            sender.sendMessage(feedback.formatInvalidUsage(resolveUsage(path, node)));
            return null;
        }

        return parsedValues;
    }

    private record TraversalResult(CommandNode node, int argIndex) {}

    private void executeNode(
            CommandNode node, CommandExecutionMode mode, CommandContext context, CommandSender sender) {
        switch (mode) {
            case SYNC -> {
                try {
                    var handler = node.syncHandler();
                    if (handler != null) {
                        handler.execute(context);
                    }
                } catch (Exception failure) {
                    handleExecutionFailure(sender, node, failure);
                }
            }
            case ASYNC -> {
                scheduler.async("command-" + node.name(), () -> {
                    try {
                        var handler = node.asyncHandler();
                        if (handler != null) {
                            var stage = handler.executeAsync(context);
                            if (stage != null) {
                                stage.whenComplete((_, failure) -> {
                                    if (failure != null) {
                                        handleExecutionFailure(sender, node, failure);
                                    }
                                });
                            }
                        }
                    } catch (Exception failure) {
                        handleExecutionFailure(sender, node, failure);
                    }
                });
            }
            case ENTITY_REGION -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(feedback.formatPlayerOnly());
                    return;
                }
                scheduler.entity(player, () -> {
                    try {
                        var handler = node.entityHandler();
                        if (handler != null) {
                            handler.executeEntity(context, player);
                        }
                    } catch (Exception failure) {
                        handleExecutionFailure(sender, node, failure);
                    }
                });
            }
        }
    }

    public List<String> complete(CommandNode rootNode, CommandSender sender, String alias, List<String> rawArgs) {
        Objects.requireNonNull(rootNode, "rootNode");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(rawArgs, "rawArgs");

        if (rawArgs.isEmpty()) {
            return List.of();
        }

        var currentNode = rootNode;
        var argIndex = 0;

        // Traverse subcommands for all tokens EXCEPT the last one (which is currently being typed)
        while (argIndex < rawArgs.size() - 1) {
            var token = rawArgs.get(argIndex);
            var next = currentNode.findSubcommand(token).orElse(null);
            if (next == null) {
                break;
            }
            currentNode = next;
            argIndex++;
        }

        var currentToken = rawArgs.getLast();
        var currentTokenLower = currentToken.toLowerCase(Locale.ROOT);
        var suggestions = new java.util.LinkedHashSet<String>();

        // If at subcommand level, suggest matching subcommands that sender has permission for
        if (currentNode.hasSubcommands()) {
            for (var entry : currentNode.subcommands().entrySet()) {
                var name = entry.getKey();
                var child = entry.getValue();
                if (name.startsWith(currentTokenLower) && child.permission().test(sender)) {
                    suggestions.add(name);
                }
            }
        }

        // If at arguments level, suggest for current argument
        var argumentPos = (rawArgs.size() - 1) - argIndex;
        if (argumentPos >= 0 && argumentPos < currentNode.arguments().size()) {
            var argument = currentNode.arguments().get(argumentPos);
            var suggestionContext = new SuggestionContext(sender, rawArgs, currentToken);
            var argSuggestions = argument.suggester().suggest(suggestionContext);
            suggestions.addAll(argSuggestions);
        }

        return List.copyOf(suggestions);
    }

    private void handleExecutionFailure(CommandSender sender, CommandNode node, Throwable failure) {
        var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;

        if (cause instanceof CommandExecutionException cmdEx
                && cmdEx.userMessage().isPresent()) {
            deliver(sender, cmdEx.userMessage().get());
            return;
        }

        plugin.getLogger()
                .log(Level.SEVERE, cause, () -> "Unhandled exception executing command '/" + node.name() + "'");
        deliver(sender, feedback.formatExecutionError());
    }

    /**
     * Delivers feedback to a sender from any thread.
     *
     * <p>Failure feedback can originate from async continuations (for example
     * {@code whenComplete} on an {@code executesAsync} stage), so player senders are resolved on
     * their owning entity thread and other senders are messaged from the global scheduler.
     */
    private void deliver(CommandSender sender, Component component) {
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

    private String resolveUsage(List<String> path, CommandNode node) {
        return node.usage().orElseGet(() -> {
            var sb = new StringBuilder();
            sb.append("/").append(String.join(" ", path));

            if (node.hasSubcommands()) {
                sb.append(" <");
                var distinctSubcommands = node.subcommands().values().stream()
                        .map(CommandNode::name)
                        .distinct()
                        .sorted()
                        .toList();
                sb.append(String.join("|", distinctSubcommands));
                sb.append(">");
            }

            for (var arg : node.arguments()) {
                sb.append(" ");
                var formatted = arg.isOptional() ? "[" + arg.name() + "]" : "<" + arg.name() + ">";
                sb.append(formatted);
            }

            return sb.toString();
        });
    }
}

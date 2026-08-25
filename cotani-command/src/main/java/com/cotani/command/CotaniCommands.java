package com.cotani.command;

import com.cotani.AsyncCloseable;
import com.cotani.command.api.CommandBuilder;
import com.cotani.command.api.CommandNode;
import com.cotani.command.feedback.CommandFeedback;
import com.cotani.command.internal.BukkitCommandWrapper;
import com.cotani.command.internal.DefaultCommandDispatcher;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.util.CompletionStages;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;

/**
 * Main entry point and lifecycle manager for the Cotani command framework.
 *
 * <p>Registers commands to the server's {@link CommandMap} and unregisters them cleanly on shutdown,
 * for example via {@code Cotani.forPlugin(plugin).with(CotaniCommands.create(plugin, scheduler))}.
 */
public final class CotaniCommands implements AutoCloseable, AsyncCloseable {
    private final Plugin plugin;
    private final PaperTaskScheduler scheduler;
    private final CommandFeedback feedback;
    private final DefaultCommandDispatcher dispatcher;
    private final Map<String, BukkitCommandWrapper> registeredCommands = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private CotaniCommands(Plugin plugin, PaperTaskScheduler scheduler, CommandFeedback feedback) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.dispatcher = new DefaultCommandDispatcher(plugin, scheduler, feedback);
    }

    /**
     * Creates a new CotaniCommands manager with default feedback messages.
     *
     * @param plugin the owning plugin
     * @param scheduler the task scheduler for dispatch transitions
     * @return commands manager
     */
    public static CotaniCommands create(Plugin plugin, PaperTaskScheduler scheduler) {
        return create(plugin, scheduler, CommandFeedback.defaultFeedback());
    }

    /**
     * Creates a new CotaniCommands manager with custom feedback messages.
     *
     * @param plugin the owning plugin
     * @param scheduler the task scheduler for dispatch transitions
     * @param feedback the feedback message provider
     * @return commands manager
     */
    public static CotaniCommands create(Plugin plugin, PaperTaskScheduler scheduler, CommandFeedback feedback) {
        return new CotaniCommands(plugin, scheduler, feedback);
    }

    /**
     * Helper to start building a command node.
     *
     * @param name command primary name
     * @return command builder
     */
    public static CommandBuilder builder(String name) {
        return CommandBuilder.of(name);
    }

    /**
     * Registers a command node into Bukkit's {@link CommandMap}.
     *
     * @param command command node to register
     * @return this instance
     */
    public CotaniCommands register(CommandNode command) {
        Objects.requireNonNull(command, "command");
        ensureNotClosed();

        var commandMap = resolveCommandMap();
        var wrapper = new BukkitCommandWrapper(command, dispatcher);
        var fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);

        commandMap.register(fallbackPrefix, wrapper);
        registeredCommands.put(command.name().toLowerCase(Locale.ROOT), wrapper);
        syncCommandsSafely();

        return this;
    }

    /**
     * Builds and registers a command node using a configuration lambda.
     *
     * @param name command name
     * @param configurator builder configurator
     * @return this instance
     */
    public CotaniCommands register(String name, java.util.function.Consumer<CommandBuilder> configurator) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(configurator, "configurator");
        var builder = CommandBuilder.of(name);
        configurator.accept(builder);
        return register(builder.build());
    }

    /**
     * Registers multiple command nodes into Bukkit's {@link CommandMap}.
     *
     * @param commands command nodes to register
     * @return this instance
     */
    public CotaniCommands registerAll(CommandNode... commands) {
        Objects.requireNonNull(commands, "commands");
        for (var command : commands) {
            register(command);
        }
        return this;
    }

    /**
     * Registers a collection of command nodes into Bukkit's {@link CommandMap}.
     *
     * @param commands collection of command nodes
     * @return this instance
     */
    public CotaniCommands registerAll(Collection<CommandNode> commands) {
        Objects.requireNonNull(commands, "commands");
        for (var command : commands) {
            register(command);
        }
        return this;
    }

    /**
     * Unregisters a specific command by primary name.
     *
     * @param name command name
     */
    public void unregister(String name) {
        Objects.requireNonNull(name, "name");
        var wrapper = registeredCommands.remove(name.toLowerCase(Locale.ROOT));
        if (wrapper == null) {
            return;
        }

        var commandMap = resolveCommandMap();
        wrapper.unregister(commandMap);

        var knownCommands = commandMap.getKnownCommands();
        var prefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";
        var cmdName = wrapper.getName().toLowerCase(Locale.ROOT);

        knownCommands.remove(cmdName);
        knownCommands.remove(prefix + cmdName);

        for (var alias : wrapper.getAliases()) {
            var aliasLower = alias.toLowerCase(Locale.ROOT);
            knownCommands.remove(aliasLower);
            knownCommands.remove(prefix + aliasLower);
        }
        syncCommandsSafely();
    }

    /**
     * Unregisters all commands registered by this module.
     */
    public void unregisterAll() {
        var commandMap = resolveCommandMap();
        var knownCommands = commandMap.getKnownCommands();
        var prefix = plugin.getName().toLowerCase(Locale.ROOT) + ":";

        for (var wrapper : registeredCommands.values()) {
            wrapper.unregister(commandMap);
            var cmdName = wrapper.getName().toLowerCase(Locale.ROOT);
            knownCommands.remove(cmdName);
            knownCommands.remove(prefix + cmdName);

            for (var alias : wrapper.getAliases()) {
                var aliasLower = alias.toLowerCase(Locale.ROOT);
                knownCommands.remove(aliasLower);
                knownCommands.remove(prefix + aliasLower);
            }
        }

        registeredCommands.clear();
        syncCommandsSafely();
    }

    /**
     * Returns the configured {@link CommandFeedback}.
     *
     * @return command feedback provider
     */
    public CommandFeedback feedback() {
        return feedback;
    }

    /**
     * Returns the associated {@link PaperTaskScheduler}.
     *
     * @return task scheduler
     */
    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        unregisterAll();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        close();
        return CompletionStages.completedVoid();
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("CotaniCommands module is closed");
        }
    }

    private CommandMap resolveCommandMap() {
        return plugin.getServer().getCommandMap();
    }

    private void syncCommandsSafely() {
        try {
            var server = plugin.getServer();
            var syncMethod = server.getClass().getMethod("syncCommands");
            syncMethod.invoke(server);
        } catch (Exception exception) {
            java.util.logging.Logger.getLogger(CotaniCommands.class.getName())
                    .log(java.util.logging.Level.FINE, "Could not synchronize commands on this platform", exception);
        }
    }
}

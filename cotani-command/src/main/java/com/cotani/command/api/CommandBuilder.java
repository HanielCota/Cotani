package com.cotani.command.api;

import com.cotani.command.argument.Argument;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for constructing immutable {@link CommandNode} instances.
 */
public final class CommandBuilder {
    private final String name;
    private final Set<String> aliases = new HashSet<>();
    private @Nullable String description;
    private @Nullable String usage;
    private SenderType senderType = SenderType.ANY;
    private PermissionRequirement permission = PermissionRequirement.none();
    private CooldownEvaluator cooldown = CooldownEvaluator.none();
    private ExecutionTarget executionTarget = ExecutionTarget.SYNC;
    private final List<Argument<?>> arguments = new ArrayList<>();
    private final Map<String, CommandNode> subcommands = new HashMap<>();
    private @Nullable SyncCommandHandler syncHandler;
    private @Nullable AsyncCommandHandler asyncHandler;
    private @Nullable EntityCommandHandler entityHandler;

    private CommandBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * Creates a new command builder with the given name.
     *
     * @param name command name
     * @return builder
     */
    public static CommandBuilder of(String name) {
        return new CommandBuilder(name);
    }

    /**
     * Adds alternative aliases for this command.
     *
     * @param aliases aliases varargs
     * @return this builder
     */
    public CommandBuilder aliases(String... aliases) {
        Objects.requireNonNull(aliases, "aliases");
        for (var alias : aliases) {
            this.aliases.add(Objects.requireNonNull(alias, "alias"));
        }
        return this;
    }

    /**
     * Adds alternative aliases for this command.
     *
     * @param aliases collection of aliases
     * @return this builder
     */
    public CommandBuilder aliases(Collection<String> aliases) {
        Objects.requireNonNull(aliases, "aliases");
        for (var alias : aliases) {
            this.aliases.add(Objects.requireNonNull(alias, "alias"));
        }
        return this;
    }

    /**
     * Sets the human-readable description for this command.
     *
     * @param description description string
     * @return this builder
     */
    public CommandBuilder description(String description) {
        this.description = Objects.requireNonNull(description, "description");
        return this;
    }

    /**
     * Sets an explicit usage string format (e.g., {@code "/pay <player> <amount>"}).
     *
     * @param usage usage format
     * @return this builder
     */
    public CommandBuilder usage(String usage) {
        this.usage = Objects.requireNonNull(usage, "usage");
        return this;
    }

    /**
     * Restricts execution to the specified {@link SenderType}.
     *
     * @param senderType sender requirement
     * @return this builder
     */
    public CommandBuilder sender(SenderType senderType) {
        this.senderType = Objects.requireNonNull(senderType, "senderType");
        return this;
    }

    /**
     * Restricts execution strictly to in-game players.
     *
     * @return this builder
     */
    public CommandBuilder playerOnly() {
        return sender(SenderType.PLAYER);
    }

    /**
     * Restricts execution strictly to the server console.
     *
     * @return this builder
     */
    public CommandBuilder consoleOnly() {
        return sender(SenderType.CONSOLE);
    }

    /**
     * Sets the required Bukkit permission node.
     *
     * @param permission permission node string
     * @return this builder
     */
    public CommandBuilder permission(String permission) {
        Objects.requireNonNull(permission, "permission");
        this.permission = PermissionRequirement.of(permission);
        return this;
    }

    /**
     * Sets the required permission requirement.
     *
     * @param permission permission requirement
     * @return this builder
     */
    public CommandBuilder permission(PermissionRequirement permission) {
        this.permission = Objects.requireNonNull(permission, "permission");
        return this;
    }

    /**
     * Applies a duration-based in-memory cooldown to this command.
     *
     * @param duration cooldown duration
     * @return this builder
     */
    public CommandBuilder cooldown(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        this.cooldown = CooldownEvaluator.of(duration);
        return this;
    }

    /**
     * Applies a {@link com.cotani.cooldown.api.CooldownService}-backed cooldown to this command.
     *
     * @param cooldownService cooldown service instance
     * @param duration cooldown duration
     * @return this builder
     */
    public CommandBuilder cooldown(com.cotani.cooldown.api.CooldownService cooldownService, Duration duration) {
        Objects.requireNonNull(cooldownService, "cooldownService");
        Objects.requireNonNull(duration, "duration");
        this.cooldown = CooldownEvaluator.of(cooldownService, duration);
        return this;
    }

    /**
     * Sets a custom cooldown evaluator.
     *
     * @param cooldown cooldown evaluator
     * @return this builder
     */
    public CommandBuilder cooldown(CooldownEvaluator cooldown) {
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        return this;
    }

    /**
     * Appends an argument definition to this command node.
     *
     * @param argument argument to append
     * @return this builder
     */
    public CommandBuilder argument(Argument<?> argument) {
        Objects.requireNonNull(argument, "argument");
        this.arguments.add(argument);
        return this;
    }

    /**
     * Appends multiple argument definitions to this command node.
     *
     * @param arguments arguments to append
     * @return this builder
     */
    public CommandBuilder arguments(Argument<?>... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        for (var arg : arguments) {
            argument(arg);
        }
        return this;
    }

    /**
     * Adds a child subcommand node.
     *
     * @param subcommand child command node
     * @return this builder
     */
    public CommandBuilder subcommand(CommandNode subcommand) {
        Objects.requireNonNull(subcommand, "subcommand");
        this.subcommands.put(subcommand.name(), subcommand);
        for (var alias : subcommand.aliases()) {
            this.subcommands.put(alias, subcommand);
        }
        return this;
    }

    /**
     * Builds and adds a child subcommand.
     *
     * @param subcommandBuilder child command builder
     * @return this builder
     */
    public CommandBuilder subcommand(CommandBuilder subcommandBuilder) {
        Objects.requireNonNull(subcommandBuilder, "subcommandBuilder");
        return subcommand(subcommandBuilder.build());
    }

    /**
     * Configures and adds a child subcommand using a builder consumer.
     *
     * @param name subcommand name
     * @param configurator configurator consumer
     * @return this builder
     */
    public CommandBuilder subcommand(String name, java.util.function.Consumer<CommandBuilder> configurator) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(configurator, "configurator");
        var builder = new CommandBuilder(name);
        configurator.accept(builder);
        return subcommand(builder.build());
    }

    /**
     * Registers a synchronous handler for this command node.
     *
     * @param handler execution handler
     * @return this builder
     */
    public CommandBuilder executes(SyncCommandHandler handler) {
        Objects.requireNonNull(handler, "handler");
        this.executionTarget = ExecutionTarget.SYNC;
        this.syncHandler = handler;
        this.asyncHandler = null;
        this.entityHandler = null;
        return this;
    }

    /**
     * Registers an asynchronous handler for this command node.
     *
     * @param handler execution handler
     * @return this builder
     */
    public CommandBuilder executesAsync(AsyncCommandHandler handler) {
        Objects.requireNonNull(handler, "handler");
        this.executionTarget = ExecutionTarget.ASYNC;
        this.asyncHandler = handler;
        this.syncHandler = null;
        this.entityHandler = null;
        return this;
    }

    /**
     * Registers a player entity/region thread handler (Paper and Folia safe) for this command node.
     *
     * <p>Automatically implies {@link #playerOnly()}.
     *
     * @param handler execution handler
     * @return this builder
     */
    public CommandBuilder executesEntity(EntityCommandHandler handler) {
        Objects.requireNonNull(handler, "handler");
        this.senderType = SenderType.PLAYER;
        this.executionTarget = ExecutionTarget.ENTITY_REGION;
        this.entityHandler = handler;
        this.syncHandler = null;
        this.asyncHandler = null;
        return this;
    }

    /**
     * Builds an immutable {@link CommandNode}.
     *
     * @return built node
     */
    public CommandNode build() {
        return new DefaultCommandNode(
                name,
                aliases,
                description,
                usage,
                senderType,
                permission,
                cooldown,
                executionTarget,
                arguments,
                subcommands,
                syncHandler,
                asyncHandler,
                entityHandler);
    }
}

package com.cotani.command.api;

import com.cotani.command.argument.Argument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Immutable tree node representing a command or subcommand with its arguments and execution targets.
 */
public interface CommandNode {
    /**
     * Primary name of the command or subcommand.
     *
     * @return name
     */
    String name();

    /**
     * Set of alternative aliases for this node.
     *
     * @return immutable set of aliases
     */
    Set<String> aliases();

    /**
     * Optional description of what this command does.
     *
     * @return description
     */
    Optional<String> description();

    /**
     * Optional explicit usage string (e.g. {@code "/pay <player> <amount>"}).
     *
     * @return usage string
     */
    Optional<String> usage();

    /**
     * Sender restriction for this node.
     *
     * @return sender type
     */
    SenderType senderType();

    /**
     * Permission requirement to execute or view this node.
     *
     * @return permission requirement
     */
    PermissionRequirement permission();

    /**
     * Cooldown evaluator for this node.
     *
     * @return cooldown evaluator
     */
    CooldownEvaluator cooldown();

    /**
     * Execution target (Sync, Async, or Entity Region).
     *
     * @return execution target
     */
    ExecutionTarget executionTarget();

    /**
     * List of argument definitions expected by this command node.
     *
     * @return immutable list of arguments
     */
    List<Argument<?>> arguments();

    /**
     * Map of subcommands keyed by name and alias in lowercase.
     *
     * @return immutable map of subcommands
     */
    Map<String, CommandNode> subcommands();

    /**
     * Checks if this node contains any subcommands.
     *
     * @return {@code true} if subcommands exist
     */
    boolean hasSubcommands();

    /**
     * Finds a child subcommand by name or alias.
     *
     * @param name subcommand name or alias
     * @return matching subcommand node or empty
     */
    Optional<CommandNode> findSubcommand(String name);

    /**
     * Checks whether this node has an executable handler attached.
     *
     * @return {@code true} if executable
     */
    boolean canExecute();

    /**
     * Returns the synchronous handler if registered.
     *
     * @return sync handler or null
     */
    default @Nullable SyncCommandHandler syncHandler() {
        return null;
    }

    /**
     * Returns the asynchronous handler if registered.
     *
     * @return async handler or null
     */
    default @Nullable AsyncCommandHandler asyncHandler() {
        return null;
    }

    /**
     * Returns the entity/region handler if registered.
     *
     * @return entity handler or null
     */
    default @Nullable EntityCommandHandler entityHandler() {
        return null;
    }
}

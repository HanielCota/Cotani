package com.cotani.command.api;

import com.cotani.command.argument.Argument;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Immutable node representing a command or subcommand with its execution targets.
 */
final class DefaultCommandNode implements CommandNode {
    private final String name;
    private final Set<String> aliases;
    private final @Nullable String description;
    private final @Nullable String usage;
    private final SenderType senderType;
    private final PermissionRequirement permission;
    private final CooldownEvaluator cooldown;
    private final ExecutionTarget executionTarget;
    private final List<Argument<?>> arguments;
    private final Map<String, CommandNode> subcommands;
    private final @Nullable SyncCommandHandler syncHandler;
    private final @Nullable AsyncCommandHandler asyncHandler;
    private final @Nullable EntityCommandHandler entityHandler;

    DefaultCommandNode(
            String name,
            Set<String> aliases,
            @Nullable String description,
            @Nullable String usage,
            SenderType senderType,
            PermissionRequirement permission,
            CooldownEvaluator cooldown,
            ExecutionTarget executionTarget,
            List<Argument<?>> arguments,
            Map<String, CommandNode> subcommands,
            @Nullable SyncCommandHandler syncHandler,
            @Nullable AsyncCommandHandler asyncHandler,
            @Nullable EntityCommandHandler entityHandler) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        var aliasSet = new java.util.HashSet<String>();
        for (var alias : Objects.requireNonNull(aliases, "aliases")) {
            aliasSet.add(alias.toLowerCase(Locale.ROOT));
        }
        this.aliases = Set.copyOf(aliasSet);
        this.description = description;
        this.usage = usage;
        this.senderType = Objects.requireNonNull(senderType, "senderType");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        this.executionTarget = Objects.requireNonNull(executionTarget, "executionTarget");
        this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));

        var subMap = new HashMap<String, CommandNode>();
        for (var entry : Objects.requireNonNull(subcommands, "subcommands").entrySet()) {
            subMap.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        this.subcommands = Map.copyOf(subMap);
        this.syncHandler = syncHandler;
        this.asyncHandler = asyncHandler;
        this.entityHandler = entityHandler;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<String> aliases() {
        return aliases;
    }

    @Override
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    @Override
    public Optional<String> usage() {
        return Optional.ofNullable(usage);
    }

    @Override
    public SenderType senderType() {
        return senderType;
    }

    @Override
    public PermissionRequirement permission() {
        return permission;
    }

    @Override
    public CooldownEvaluator cooldown() {
        return cooldown;
    }

    @Override
    public ExecutionTarget executionTarget() {
        return executionTarget;
    }

    @Override
    public List<Argument<?>> arguments() {
        return arguments;
    }

    @Override
    public Map<String, CommandNode> subcommands() {
        return subcommands;
    }

    @Override
    public boolean hasSubcommands() {
        return !subcommands.isEmpty();
    }

    @Override
    public Optional<CommandNode> findSubcommand(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(subcommands.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public boolean canExecute() {
        return syncHandler != null || asyncHandler != null || entityHandler != null;
    }

    @Override
    public @Nullable SyncCommandHandler syncHandler() {
        return syncHandler;
    }

    @Override
    public @Nullable AsyncCommandHandler asyncHandler() {
        return asyncHandler;
    }

    @Override
    public @Nullable EntityCommandHandler entityHandler() {
        return entityHandler;
    }
}

package com.cotani.command.internal;

import com.cotani.api.InternalApi;
import com.cotani.command.api.CommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

/**
 * Bukkit {@link Command} bridge wrapping a Cotani {@link CommandNode}.
 */
@InternalApi
public final class BukkitCommandWrapper extends Command {
    private final CommandNode node;
    private final DefaultCommandDispatcher dispatcher;

    public BukkitCommandWrapper(CommandNode node, DefaultCommandDispatcher dispatcher) {
        super(node.name());
        this.node = Objects.requireNonNull(node, "node");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");

        node.description().ifPresent(this::setDescription);
        node.usage().ifPresent(this::setUsage);
        setAliases(new ArrayList<>(node.aliases()));
        node.permission().node().ifPresent(this::setPermission);
    }

    public CommandNode node() {
        return node;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        var rawArgs = (args == null || args.length == 0) ? List.<String>of() : List.of(args);
        dispatcher.dispatch(node, sender, commandLabel, rawArgs);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        var rawArgs = (args == null || args.length == 0) ? List.<String>of() : List.of(args);
        return dispatcher.complete(node, sender, alias, rawArgs);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, @Nullable Location location)
            throws IllegalArgumentException {
        return tabComplete(sender, alias, args);
    }
}

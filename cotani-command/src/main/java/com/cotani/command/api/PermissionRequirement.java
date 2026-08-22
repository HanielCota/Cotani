package com.cotani.command.api;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;

/**
 * Functional check and metadata for command permissions.
 */
@FunctionalInterface
public interface PermissionRequirement {
    /**
     * Checks if the sender has permission to run the command.
     *
     * @param sender the command sender
     * @return {@code true} if allowed, {@code false} otherwise
     */
    boolean test(CommandSender sender);

    /**
     * Returns the permission node string if available.
     *
     * @return the permission node, or empty if it is a dynamic predicate
     */
    default Optional<String> node() {
        return Optional.empty();
    }

    /**
     * Creates a permission requirement based on a Bukkit permission node.
     *
     * @param node the permission string
     * @return the permission requirement
     */
    static PermissionRequirement of(String node) {
        Objects.requireNonNull(node, "node");
        return new PermissionRequirement() {
            @Override
            public boolean test(CommandSender sender) {
                Objects.requireNonNull(sender, "sender");
                return sender.hasPermission(node);
            }

            @Override
            public Optional<String> node() {
                return Optional.of(node);
            }
        };
    }

    /**
     * Creates a dynamic permission requirement based on a predicate.
     *
     * @param predicate the test predicate
     * @return the permission requirement
     */
    static PermissionRequirement predicate(Predicate<CommandSender> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return predicate::test;
    }

    /**
     * Returns a permission requirement that always allows execution.
     *
     * @return no-op permission requirement
     */
    static PermissionRequirement none() {
        return _ -> true;
    }
}

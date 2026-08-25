package com.cotani.placeholder.impl;

import com.cotani.api.InternalApi;
import com.cotani.placeholder.api.PlaceholderContext;
import com.cotani.placeholder.api.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Built-in expansion providing server-wide placeholders.
 */
@InternalApi
@NullMarked
public final class BuiltinServerExpansion implements PlaceholderExpansion {

    @Override
    public String identifier() {
        return "server";
    }

    @Override
    public String author() {
        return "Cotani";
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public @Nullable String onContextRequest(PlaceholderContext context, String params) {
        try {
            var server = Bukkit.getServer();
            if (server == null) {
                return switch (params.toLowerCase(java.util.Locale.ROOT)) {
                    case "online" -> "0";
                    case "max_players" -> "100";
                    case "name" -> "Paper";
                    case "version" -> "1.21";
                    case "tps" -> "20.00";
                    default -> null;
                };
            }

            return switch (params.toLowerCase(java.util.Locale.ROOT)) {
                case "online" -> String.valueOf(server.getOnlinePlayers().size());
                case "max_players" -> String.valueOf(server.getMaxPlayers());
                case "name" -> server.getName();
                case "version" -> server.getMinecraftVersion();
                case "tps" -> {
                    double[] tps = server.getTPS();
                    yield tps.length > 0 ? String.format(java.util.Locale.ROOT, "%.2f", tps[0]) : "20.00";
                }
                default -> null;
            };
        } catch (Exception exception) {
            java.util.logging.Logger.getLogger(BuiltinServerExpansion.class.getName())
                    .log(java.util.logging.Level.FINE, "Could not resolve server placeholder", exception);
            return null;
        }
    }
}

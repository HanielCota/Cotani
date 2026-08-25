package com.cotani.placeholder.impl;

import com.cotani.api.InternalApi;
import com.cotani.placeholder.api.PlaceholderContext;
import com.cotani.placeholder.api.PlaceholderExpansion;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Built-in expansion providing standard player and server placeholders.
 */
@InternalApi
@NullMarked
public final class BuiltinPlayerExpansion implements PlaceholderExpansion {

    @Override
    public String identifier() {
        return "player";
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
        Optional<Player> maybePlayer = context.player();
        if (maybePlayer.isEmpty()) {
            if (params.equalsIgnoreCase("name") && context.viewerId() != null) {
                try {
                    var offline = org.bukkit.Bukkit.getOfflinePlayer(context.viewerId());
                    return offline.getName() != null
                            ? offline.getName()
                            : context.viewerId().toString();
                } catch (Throwable ignored) {
                    return context.viewerId().toString();
                }
            }
            return null;
        }

        Player player = maybePlayer.get();
        var loc = player.getLocation();

        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "name" -> player.getName();
            case "displayname" ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(player.displayName());
            case "uuid" -> player.getUniqueId().toString();
            case "ping" -> String.valueOf(player.getPing());
            case "world" -> {
                var world = player.getWorld();
                yield world != null ? world.getName() : "";
            }
            case "x" -> loc != null ? String.valueOf(loc.getBlockX()) : "0";
            case "y" -> loc != null ? String.valueOf(loc.getBlockY()) : "0";
            case "z" -> loc != null ? String.valueOf(loc.getBlockZ()) : "0";
            case "health" -> String.valueOf((int) player.getHealth());
            case "max_health" -> {
                var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                yield attr != null ? String.valueOf((long) attr.getValue()) : "20";
            }
            case "food" -> String.valueOf(player.getFoodLevel());
            case "level" -> String.valueOf(player.getLevel());
            case "exp" -> String.valueOf(Math.round(player.getExp() * 100));
            case "gamemode" -> player.getGameMode().name().toLowerCase(java.util.Locale.ROOT);
            case "locale" -> player.locale().toLanguageTag();
            default -> null;
        };
    }
}

package com.cotani.placeholder.internal;

import com.cotani.api.InternalApi;
import com.cotani.placeholder.api.PlaceholderContext;
import com.cotani.placeholder.api.PlaceholderService;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Optional bidirectional bridge with PlaceholderAPI (PAPI).
 *
 * <p>When PlaceholderAPI is installed and enabled, registers Cotani expansions with PAPI and
 * delegates unknown placeholders to PAPI's engine.
 */
@InternalApi
@NullMarked
public final class PlaceholderApiBridge {

    private final Plugin plugin;
    private final boolean papiPresent;
    private @Nullable Object registeredPapiExpansion;

    public PlaceholderApiBridge(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        this.papiPresent = checkPapiPresent();
    }

    private static boolean checkPapiPresent() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        } catch (Exception exception) {
            java.util.logging.Logger.getLogger(PlaceholderApiBridge.class.getName())
                    .log(java.util.logging.Level.FINE, "PlaceholderAPI is not available", exception);
            return false;
        }
    }

    public boolean isPapiPresent() {
        return papiPresent && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public void register(PlaceholderService placeholderService) {
        if (!isPapiPresent()) {
            return;
        }

        try {
            var expansion = new PapiExpansionImpl(plugin, placeholderService);
            expansion.register();
            this.registeredPapiExpansion = expansion;
        } catch (Exception throwable) {
            plugin.getLogger().warning("Failed to hook into PlaceholderAPI: " + throwable.getMessage());
        }
    }

    public void unregister() {
        if (registeredPapiExpansion instanceof me.clip.placeholderapi.expansion.PlaceholderExpansion expansion) {
            try {
                expansion.unregister();
            } catch (Exception exception) {
                plugin.getLogger()
                        .log(java.util.logging.Level.FINE, "Could not unregister PlaceholderAPI expansion", exception);
            }
            this.registeredPapiExpansion = null;
        }
    }

    public @Nullable String resolveExternal(PlaceholderContext context, String fullToken) {
        if (!isPapiPresent()) {
            return null;
        }

        try {
            Player player = context.player().orElse(null);
            String result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%" + fullToken + "%");
            if (result.equals("%" + fullToken + "%")) {
                return null;
            }
            return result;
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "Could not resolve external placeholder", exception);
            return null;
        }
    }

    public @Nullable String resolveExternalRelational(Player viewer, Player target, String fullToken) {
        if (!isPapiPresent()) {
            return null;
        }

        try {
            String result = me.clip.placeholderapi.PlaceholderAPI.setRelationalPlaceholders(
                    viewer, target, "%" + fullToken + "%");
            if (result.equals("%" + fullToken + "%")) {
                return null;
            }
            return result;
        } catch (Exception exception) {
            plugin.getLogger()
                    .log(java.util.logging.Level.FINE, "Could not resolve relational external placeholder", exception);
            return null;
        }
    }

    private static final class PapiExpansionImpl extends me.clip.placeholderapi.expansion.PlaceholderExpansion
            implements me.clip.placeholderapi.expansion.Relational {

        private final Plugin plugin;
        private final PlaceholderService placeholderService;

        private PapiExpansionImpl(Plugin plugin, PlaceholderService placeholderService) {
            this.plugin = plugin;
            this.placeholderService = placeholderService;
        }

        @Override
        public String getIdentifier() {
            return "cotani";
        }

        @Override
        public String getAuthor() {
            return String.join(", ", plugin.getPluginMeta().getAuthors());
        }

        @Override
        public String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onPlaceholderRequest(@Nullable Player player, String params) {
            return placeholderService.parse(player, "{" + params + "}");
        }

        @Override
        public @Nullable String onPlaceholderRequest(Player viewer, Player target, String params) {
            return placeholderService.parseRelational(viewer, target, "{" + params + "}");
        }
    }
}

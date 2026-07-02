package com.cotani.text;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.NullMarked;

/**
 * Centralizes the standard Adventure component serializers as reusable, immutable singletons.
 *
 * <p>Adventure serializers are thread-safe and can be shared across the application. This class
 * provides a single point of access for the most common serializers, avoiding repeated lookups and
 * scattered imports.
 *
 * <p>For custom serializer configurations (strict MiniMessage, custom tag resolvers, legacy RGB
 * support, etc.), create dedicated instances and expose them through application-specific constants.
 */
@NullMarked
public final class ComponentSerializers {

    /**
     * The standard Gson component serializer for modern Minecraft JSON chat components.
     *
     * <p>Prefer this serializer for persistence, configuration files, and databases where RGB
     * colors and modern hover events are acceptable.
     */
    public static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    /**
     * The plain text serializer. Useful for logging, clearing formatting, or extracting raw text.
     */
    public static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /**
     * The legacy serializer using the section symbol ({@code §}). Useful for legacy compatibility
     * or displaying formatted text in old clients.
     */
    public static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    /**
     * The legacy serializer using the ampersand symbol ({@code &}). Commonly used in configuration
     * files and command outputs.
     */
    public static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    /**
     * The default MiniMessage serializer with the standard tag set.
     */
    public static final MiniMessage MINIMESSAGE = MiniMessage.miniMessage();

    private ComponentSerializers() {}
}

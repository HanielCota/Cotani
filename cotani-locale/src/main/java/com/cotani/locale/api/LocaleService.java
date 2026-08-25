package com.cotani.locale.api;

import com.cotani.AsyncCloseable;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.NullMarked;

/** Resolves player locales and renders localized trusted MiniMessage templates. */
@NullMarked
public interface LocaleService extends AsyncCloseable {
    /** Returns the current immutable catalog. */
    LocaleCatalog catalog();

    /** Returns the supported locale identifiers. */
    Set<LocaleId> locales();

    /** Finds the explicitly stored locale for a player. */
    Optional<LocaleId> findPlayerLocale(UUID playerId);

    /** Returns the player's explicit locale or the catalog default. */
    LocaleId resolveLocale(UUID playerId);

    /** Renders a message using the player's resolved locale on the calling thread. */
    Component render(UUID playerId, MessageKey key, MessageArguments arguments);

    /** Renders a message using the supplied locale on the calling thread. */
    Component render(LocaleId locale, MessageKey key, MessageArguments arguments);

    /** Replaces or adds a bundle atomically for future renders. */
    void registerBundle(MessageBundle bundle);

    /** Removes a bundle and reports whether it existed. */
    boolean unregisterBundle(LocaleId locale);

    /** Persists and then publishes a player's locale preference. */
    CompletionStage<Void> setPlayerLocaleAsync(UUID playerId, LocaleId locale);

    /** Persists and then removes a player's explicit locale preference. */
    CompletionStage<Void> removePlayerLocaleAsync(UUID playerId);
}

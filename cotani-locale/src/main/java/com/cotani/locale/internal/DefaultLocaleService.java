package com.cotani.locale.internal;

import com.cotani.api.InternalApi;
import com.cotani.locale.api.LocaleCatalog;
import com.cotani.locale.api.LocaleId;
import com.cotani.locale.api.LocaleRepository;
import com.cotani.locale.api.LocaleService;
import com.cotani.locale.api.LocaleServiceOptions;
import com.cotani.locale.api.MessageArguments;
import com.cotani.locale.api.MessageBundle;
import com.cotani.locale.api.MessageKey;
import com.cotani.locale.api.MissingMessageException;
import com.cotani.text.MiniMessages;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultLocaleService implements LocaleService {
    private final AtomicReference<LocaleCatalog> catalog;
    private final ConcurrentMap<UUID, LocaleId> playerLocales = new ConcurrentHashMap<>();
    private final @Nullable LocaleRepository repository;
    private final LocaleServiceOptions options;
    private final Object mutationLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletionStage<Void> persistenceTail = completedVoid();

    public DefaultLocaleService(LocaleCatalog catalog, @Nullable LocaleRepository repository) {
        this(catalog, repository, LocaleServiceOptions.defaults());
    }

    public DefaultLocaleService(
            LocaleCatalog catalog, @Nullable LocaleRepository repository, LocaleServiceOptions options) {
        this.catalog = new AtomicReference<>(Objects.requireNonNull(catalog, "catalog"));
        this.repository = repository;
        this.options = Objects.requireNonNull(options, "options");
    }

    public void loadPlayerLocales(Map<UUID, LocaleId> locales) {
        Objects.requireNonNull(locales, "locales")
                .forEach((playerId, locale) -> playerLocales.put(
                        Objects.requireNonNull(playerId, "playerId"), Objects.requireNonNull(locale, "locale")));
    }

    @Override
    public LocaleCatalog catalog() {
        ensureOpen();
        return catalogSnapshot();
    }

    @Override
    public Set<LocaleId> locales() {
        return catalog().locales();
    }

    @Override
    public Optional<LocaleId> findPlayerLocale(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ensureOpen();
        return Optional.ofNullable(playerLocales.get(playerId));
    }

    @Override
    public LocaleId resolveLocale(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        ensureOpen();
        return Optional.ofNullable(playerLocales.get(playerId))
                .orElseGet(() -> catalogSnapshot().defaultLocale());
    }

    @Override
    public Component render(UUID playerId, MessageKey key, MessageArguments arguments) {
        return render(resolveLocale(playerId), key, arguments);
    }

    @Override
    public Component render(LocaleId locale, MessageKey key, MessageArguments arguments) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(arguments, "arguments");
        ensureOpen();
        var snapshot = catalogSnapshot();
        var message = snapshot.find(locale, key)
                .orElseThrow(() -> new MissingMessageException(locale, key, snapshot.fallbackChain(locale)));
        return MiniMessages.parse(message.template(), arguments.resolvers());
    }

    @Override
    public void registerBundle(MessageBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        ensureOpen();
        catalog.updateAndGet(current -> current.withBundle(bundle));
    }

    @Override
    public boolean unregisterBundle(LocaleId locale) {
        Objects.requireNonNull(locale, "locale");
        ensureOpen();
        var previous = catalog.getAndUpdate(current -> current.withoutBundle(locale));
        return previous.bundles().containsKey(locale);
    }

    @Override
    public CompletionStage<Void> setPlayerLocaleAsync(UUID playerId, LocaleId locale) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(locale, "locale");
        return enqueuePersistence(() -> persistSet(playerId, locale));
    }

    @Override
    public CompletionStage<Void> removePlayerLocaleAsync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return enqueuePersistence(() -> persistRemove(playerId));
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (mutationLock) {
            if (!closed.compareAndSet(false, true)) {
                return persistenceTail;
            }
            playerLocales.clear();
            return persistenceTail;
        }
    }

    private CompletionStage<Void> persistSet(UUID playerId, LocaleId locale) {
        if (repository == null) {
            playerLocales.put(playerId, locale);
            return completedVoid();
        }
        return options.withTimeout(
                        Objects.requireNonNull(repository.saveAsync(playerId, locale), "repository save stage"))
                .thenRun(() -> playerLocales.put(playerId, locale));
    }

    private CompletionStage<Void> persistRemove(UUID playerId) {
        if (repository == null) {
            playerLocales.remove(playerId);
            return completedVoid();
        }
        return options.withTimeout(Objects.requireNonNull(repository.deleteAsync(playerId), "repository delete stage"))
                .thenRun(() -> playerLocales.remove(playerId));
    }

    private CompletionStage<Void> enqueuePersistence(Supplier<CompletionStage<Void>> operation) {
        synchronized (mutationLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            persistenceTail = persistenceTail.thenCompose(_ -> operation.get());
            return persistenceTail;
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw closedFailure();
        }
    }

    private LocaleCatalog catalogSnapshot() {
        return Objects.requireNonNull(catalog.get(), "catalog");
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Locale service is closed");
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    @SuppressWarnings("NullAway")
    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }
}

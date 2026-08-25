package com.cotani.locale;

import com.cotani.locale.api.LocaleCatalog;
import com.cotani.locale.api.LocaleId;
import com.cotani.locale.api.LocaleRepository;
import com.cotani.locale.api.LocaleService;
import com.cotani.locale.api.LocaleServiceOptions;
import com.cotani.locale.internal.DefaultLocaleService;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

/** Factories for the {@code cotani-locale} module. */
@NullMarked
public final class CotaniLocales {
    private CotaniLocales() {}

    /** Creates a locale service that keeps player preferences only in memory. */
    public static LocaleService inMemory(LocaleCatalog catalog) {
        return new DefaultLocaleService(Objects.requireNonNull(catalog, "catalog"), null);
    }

    /** Creates a repository-backed service using the default ten-second timeout. */
    public static CompletionStage<LocaleService> fromRepositoryAsync(
            LocaleCatalog catalog, LocaleRepository repository) {
        return fromRepositoryAsync(catalog, repository, LocaleServiceOptions.defaults());
    }

    /** Loads preferences and creates a repository-backed service with explicit options. */
    public static CompletionStage<LocaleService> fromRepositoryAsync(
            LocaleCatalog catalog, LocaleRepository repository, LocaleServiceOptions options) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(options, "options");
        return options.withTimeout(Objects.requireNonNull(repository.loadAsync(), "repository load stage"))
                .thenApply(locales -> createLoadedService(catalog, repository, options, locales));
    }

    private static LocaleService createLoadedService(
            LocaleCatalog catalog,
            LocaleRepository repository,
            LocaleServiceOptions options,
            Map<UUID, LocaleId> locales) {
        var service = new DefaultLocaleService(catalog, repository, options);
        service.loadPlayerLocales(Objects.requireNonNull(locales, "locales"));
        return service;
    }
}

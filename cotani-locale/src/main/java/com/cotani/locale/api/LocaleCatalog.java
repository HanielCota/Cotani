package com.cotani.locale.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/** Immutable localized message catalog with deterministic fallback resolution. */
@NullMarked
public record LocaleCatalog(LocaleId defaultLocale, Map<LocaleId, MessageBundle> bundles) {
    public LocaleCatalog {
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Objects.requireNonNull(bundles, "bundles");
        var normalized = new LinkedHashMap<LocaleId, MessageBundle>();
        bundles.forEach((locale, bundle) -> {
            Objects.requireNonNull(locale, "bundle locale");
            Objects.requireNonNull(bundle, "bundle");
            if (!locale.equals(bundle.locale())) {
                throw new IllegalArgumentException("Bundle locale does not match its map key: " + locale);
            }
            normalized.put(locale, bundle);
        });
        bundles = Map.copyOf(normalized);
    }

    public static Builder builder(LocaleId defaultLocale) {
        return new Builder(defaultLocale);
    }

    public Set<LocaleId> locales() {
        return bundles.keySet();
    }

    public Optional<ResolvedMessage> find(LocaleId requestedLocale, MessageKey key) {
        Objects.requireNonNull(requestedLocale, "requestedLocale");
        Objects.requireNonNull(key, "key");
        for (var candidate : fallbackChain(requestedLocale)) {
            var bundle = bundles.get(candidate);
            if (bundle == null) {
                continue;
            }
            var template = bundle.find(key);
            if (template.isPresent()) {
                return Optional.of(new ResolvedMessage(candidate, template.orElseThrow()));
            }
        }
        return Optional.empty();
    }

    public List<LocaleId> fallbackChain(LocaleId requestedLocale) {
        Objects.requireNonNull(requestedLocale, "requestedLocale");
        var candidates = new ArrayList<>(requestedLocale.fallbacks());
        defaultLocale.fallbacks().forEach(locale -> addCandidate(candidates, locale));
        return List.copyOf(candidates);
    }

    public LocaleCatalog withBundle(MessageBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        var updated = new LinkedHashMap<>(bundles);
        updated.put(bundle.locale(), bundle);
        return new LocaleCatalog(defaultLocale, updated);
    }

    public LocaleCatalog withoutBundle(LocaleId locale) {
        Objects.requireNonNull(locale, "locale");
        var updated = new LinkedHashMap<>(bundles);
        updated.remove(locale);
        return new LocaleCatalog(defaultLocale, updated);
    }

    public record ResolvedMessage(LocaleId locale, String template) {
        public ResolvedMessage {
            Objects.requireNonNull(locale, "locale");
            Objects.requireNonNull(template, "template");
        }
    }

    public static final class Builder {
        private final LocaleId defaultLocale;
        private final Map<LocaleId, MessageBundle> bundles = new LinkedHashMap<>();

        private Builder(LocaleId defaultLocale) {
            this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
        }

        public Builder bundle(MessageBundle bundle) {
            Objects.requireNonNull(bundle, "bundle");
            bundles.put(bundle.locale(), bundle);
            return this;
        }

        public LocaleCatalog build() {
            return new LocaleCatalog(defaultLocale, bundles);
        }
    }

    private static void addCandidate(List<LocaleId> candidates, LocaleId candidate) {
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }
}

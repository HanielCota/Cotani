package com.cotani.locale.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/** Canonical BCP 47 locale identifier used by a Cotani message catalog. */
@NullMarked
public record LocaleId(String languageTag) {
    private static final int MAX_LENGTH = 32;

    public LocaleId {
        Objects.requireNonNull(languageTag, "languageTag");
        var normalized = languageTag.trim().replace('_', '-');
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Locale language tag must contain 1 to " + MAX_LENGTH + " characters");
        }
        if (normalized.startsWith("-")
                || normalized.endsWith("-")
                || normalized.contains("--")
                || !normalized
                        .chars()
                        .allMatch(character -> Character.isLetterOrDigit(character) || character == '-')) {
            throw new IllegalArgumentException("Invalid locale language tag: " + languageTag);
        }

        var parsed = Locale.forLanguageTag(normalized);
        if (parsed.equals(Locale.ROOT) || parsed.getLanguage().isEmpty()) {
            throw new IllegalArgumentException("Invalid locale language tag: " + languageTag);
        }
        languageTag = parsed.toLanguageTag();
    }

    public static LocaleId of(String languageTag) {
        return new LocaleId(languageTag);
    }

    /** Returns the language-only fallback, or empty when this identifier is already language-only. */
    public Optional<LocaleId> languageOnly() {
        var parsed = Locale.forLanguageTag(languageTag);
        if (parsed.getCountry().isEmpty()
                && parsed.getScript().isEmpty()
                && parsed.getVariant().isEmpty()
                && parsed.getUnicodeLocaleKeys().isEmpty()
                && parsed.getExtensionKeys().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new LocaleId(parsed.getLanguage()));
    }

    /**
     * Returns locale fallbacks from the most specific representation to the language-only form.
     * Scripts are retained, so {@code zh-Hant-TW} can resolve {@code zh-Hant} before {@code zh}.
     */
    public List<LocaleId> fallbacks() {
        var parsed = Locale.forLanguageTag(languageTag);
        var fallbacks = new ArrayList<LocaleId>();
        addFallback(fallbacks, this);

        var language = parsed.getLanguage();
        var script = parsed.getScript();
        var country = parsed.getCountry();
        if (!script.isEmpty() || !country.isEmpty()) {
            addFallback(fallbacks, createBaseLocale(language, script, country));
        }
        if (!script.isEmpty()) {
            addFallback(fallbacks, createBaseLocale(language, script, ""));
        }
        if (!country.isEmpty()) {
            addFallback(fallbacks, createBaseLocale(language, "", country));
        }
        addFallback(fallbacks, new LocaleId(language));

        return List.copyOf(fallbacks);
    }

    private static LocaleId createBaseLocale(String language, String script, String country) {
        var builder = new Locale.Builder().setLanguage(language);
        if (!script.isEmpty()) {
            builder.setScript(script);
        }
        if (!country.isEmpty()) {
            builder.setRegion(country);
        }
        return new LocaleId(builder.build().toLanguageTag());
    }

    private static void addFallback(List<LocaleId> fallbacks, LocaleId candidate) {
        if (!fallbacks.contains(candidate)) {
            fallbacks.add(candidate);
        }
    }
}

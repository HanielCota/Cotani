package com.cotani.locale.api;

import com.cotani.text.Placeholders;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.NullMarked;

/** Immutable MiniMessage arguments used while rendering a localized template. */
@NullMarked
public final class MessageArguments {
    private final List<TagResolver> resolvers;

    private MessageArguments(List<TagResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    public static MessageArguments empty() {
        return new MessageArguments(List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns a defensive array suitable for {@code MiniMessages.parse}. */
    public TagResolver[] resolvers() {
        return resolvers.toArray(TagResolver[]::new);
    }

    public static final class Builder {
        private final List<TagResolver> resolvers = new ArrayList<>();
        private final Set<String> namedKeys = new HashSet<>();

        public Builder text(String key, String value) {
            addNamed(key, Placeholders.unparsed(key, value));
            return this;
        }

        public Builder component(String key, ComponentLike value) {
            addNamed(key, Placeholders.component(key, value));
            return this;
        }

        public Builder number(String key, Number value) {
            addNamed(key, Placeholders.number(key, value));
            return this;
        }

        public Builder date(String key, TemporalAccessor value) {
            addNamed(key, Placeholders.date(key, value));
            return this;
        }

        /** Adds a MiniMessage choice formatter, commonly used for plural forms. */
        public Builder plural(String key, Number value) {
            addNamed(key, Placeholders.choice(key, value));
            return this;
        }

        public Builder booleanChoice(String key, boolean value) {
            addNamed(key, Placeholders.booleanChoice(key, value));
            return this;
        }

        /** Adds an advanced resolver supplied by the caller. */
        public Builder resolver(TagResolver resolver) {
            resolvers.add(Objects.requireNonNull(resolver, "resolver"));
            return this;
        }

        public MessageArguments build() {
            return new MessageArguments(resolvers);
        }

        private void addNamed(String key, TagResolver resolver) {
            Objects.requireNonNull(key, "key");
            var normalizedKey = key.trim().toLowerCase(Locale.ROOT);
            if (normalizedKey.isEmpty()) {
                throw new IllegalArgumentException("Placeholder key must not be blank");
            }
            if (!namedKeys.add(normalizedKey)) {
                throw new IllegalArgumentException("Duplicate placeholder key: " + key);
            }
            resolvers.add(resolver);
        }
    }
}

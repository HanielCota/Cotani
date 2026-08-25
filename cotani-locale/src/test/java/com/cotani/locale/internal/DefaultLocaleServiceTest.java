package com.cotani.locale.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.locale.CotaniLocales;
import com.cotani.locale.api.LocaleCatalog;
import com.cotani.locale.api.LocaleId;
import com.cotani.locale.api.LocaleRepository;
import com.cotani.locale.api.LocaleServiceOptions;
import com.cotani.locale.api.MessageArguments;
import com.cotani.locale.api.MessageBundle;
import com.cotani.locale.api.MessageKey;
import com.cotani.locale.api.MissingMessageException;
import com.cotani.text.ComponentTexts;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultLocaleServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void rendersUsingPlayerLocaleAndConfiguredFallback() {
        var service = CotaniLocales.inMemory(catalog());
        service.setPlayerLocaleAsync(PLAYER_ID, LocaleId.of("pt-BR"))
                .toCompletableFuture()
                .join();

        var rendered = service.render(
                PLAYER_ID,
                MessageKey.of("welcome"),
                MessageArguments.builder().text("name", "<red>Steve").build());

        assertEquals("Olá <red>Steve", ComponentTexts.toPlain(rendered));
    }

    @Test
    void reportsMissingMessageWithAttemptedLocales() {
        var service = CotaniLocales.inMemory(catalog());

        var exception = assertThrows(
                MissingMessageException.class,
                () -> service.render(LocaleId.of("fr-FR"), MessageKey.of("missing"), MessageArguments.empty()));

        assertEquals(
                List.of(LocaleId.of("fr-FR"), LocaleId.of("fr"), LocaleId.of("en-US"), LocaleId.of("en")),
                exception.attemptedLocales());
    }

    @Test
    void persistsPreferenceBeforeUpdatingVisibleState() {
        var repository = new RecordingRepository();
        var service = CotaniLocales.fromRepositoryAsync(catalog(), repository)
                .toCompletableFuture()
                .join();

        service.setPlayerLocaleAsync(PLAYER_ID, LocaleId.of("pt-BR"))
                .toCompletableFuture()
                .join();

        assertEquals(LocaleId.of("pt-BR"), repository.values.get(PLAYER_ID));
        assertEquals(LocaleId.of("pt-BR"), service.findPlayerLocale(PLAYER_ID).orElseThrow());
    }

    @Test
    void rejectsOperationsAfterClose() {
        var service = CotaniLocales.inMemory(catalog());
        service.closeAsync().toCompletableFuture().join();

        assertThrows(
                IllegalStateException.class,
                () -> service.render(LocaleId.of("en-US"), MessageKey.of("welcome"), MessageArguments.empty()));
    }

    @Test
    void timesOutPendingPersistenceWithoutChangingVisibleState() {
        var repository = new PendingRepository();
        var options = new LocaleServiceOptions(Duration.ofMillis(20));
        var service = CotaniLocales.fromRepositoryAsync(catalog(), repository, options)
                .toCompletableFuture()
                .join();

        var update = service.setPlayerLocaleAsync(PLAYER_ID, LocaleId.of("pt-BR"));

        assertThrows(
                CompletionException.class, () -> update.toCompletableFuture().join());
        assertTrue(service.findPlayerLocale(PLAYER_ID).isEmpty());
    }

    private static LocaleCatalog catalog() {
        var english = LocaleId.of("en-US");
        return LocaleCatalog.builder(english)
                .bundle(MessageBundle.of(english, Map.of("welcome", "Hello <name>")))
                .bundle(MessageBundle.of(LocaleId.of("pt"), Map.of("welcome", "Olá <name>")))
                .build();
    }

    private static final class RecordingRepository implements LocaleRepository {
        private final Map<UUID, LocaleId> values = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Map<UUID, LocaleId>> loadAsync() {
            return CompletableFuture.completedFuture(Map.copyOf(values));
        }

        @Override
        public CompletionStage<Void> saveAsync(UUID playerId, LocaleId locale) {
            values.put(playerId, locale);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deleteAsync(UUID playerId) {
            values.remove(playerId);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class PendingRepository implements LocaleRepository {
        @Override
        public CompletionStage<Map<UUID, LocaleId>> loadAsync() {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public CompletionStage<Void> saveAsync(UUID playerId, LocaleId locale) {
            return new CompletableFuture<>();
        }

        @Override
        public CompletionStage<Void> deleteAsync(UUID playerId) {
            return CompletableFuture.completedFuture(null);
        }
    }
}

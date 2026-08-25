package com.cotani.quest;

import com.cotani.event.api.EventBus;
import com.cotani.quest.api.QuestRepository;
import com.cotani.quest.api.QuestService;
import com.cotani.quest.api.QuestServiceOptions;
import com.cotani.quest.internal.DefaultQuestService;
import com.cotani.quest.internal.InMemoryQuestRepository;
import com.cotani.quest.storage.StorageQuestRepository;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Factories for the {@code cotani-quest} module. */
public final class CotaniQuests {
    private CotaniQuests() {}

    /** Creates an isolated in-memory service, suitable for tests or ephemeral servers. */
    public static QuestService inMemory(EventBus eventBus) {
        return fromRepository(new InMemoryQuestRepository(), eventBus);
    }

    /** Creates an in-memory service with explicit operational options. */
    public static QuestService inMemory(EventBus eventBus, QuestServiceOptions options) {
        return fromRepository(new InMemoryQuestRepository(), eventBus, options);
    }

    /** Creates a service over a caller-owned repository. */
    public static QuestService fromRepository(QuestRepository repository, EventBus eventBus) {
        return fromRepository(repository, eventBus, QuestServiceOptions.defaults());
    }

    /** Creates a service over a caller-owned repository with explicit operational options. */
    public static QuestService fromRepository(
            QuestRepository repository, EventBus eventBus, QuestServiceOptions options) {
        return fromRepository(repository, eventBus, options, Clock.systemUTC());
    }

    /** Creates a service with explicit operational options and clock. */
    public static QuestService fromRepository(
            QuestRepository repository, EventBus eventBus, QuestServiceOptions options, Clock clock) {
        return DefaultQuestService.create(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(eventBus, "eventBus"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }

    /** Creates a service backed by the caller-owned Cotani SQL storage. */
    public static QuestService storage(CotaniStorage storage, EventBus eventBus) {
        return fromRepository(new StorageQuestRepository(storage), eventBus);
    }

    /** Creates a SQL-backed service with explicit operational options. */
    public static QuestService storage(CotaniStorage storage, EventBus eventBus, QuestServiceOptions options) {
        return fromRepository(new StorageQuestRepository(storage), eventBus, options);
    }

    /** Returns the migrations required by {@link StorageQuestRepository}. */
    public static List<Migration> migrations() {
        return StorageQuestRepository.migrations();
    }
}

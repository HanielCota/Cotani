package com.cotani.quest.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the SQL table used by {@link StorageQuestRepository}. */
public final class CreateQuestTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani quest progress table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_quests_progress")
                .required("player_id", ColumnType.UUID)
                .required("quest_id", ColumnType.STRING)
                .required("progress", ColumnType.TEXT)
                .required("completed", ColumnType.BOOLEAN)
                .column("completed_at", ColumnType.TIMESTAMP)
                .column("claim_id", ColumnType.STRING)
                .column("claimed_at", ColumnType.TIMESTAMP)
                .required("revision", ColumnType.LONG)
                .primaryKey("player_id", "quest_id")
                .createIfNotExists();
    }
}

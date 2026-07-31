package com.cotani.cooldown.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds the winner token used by atomic distributed cooldown acquisition. */
public final class AddCooldownLeaseTokenMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Add distributed cooldown lease token";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        String type = schema.dialect().name().equalsIgnoreCase("sqlite") ? "TEXT" : "VARCHAR(36)";
        return schema.execute("ALTER TABLE cotani_cooldowns ADD COLUMN lease_token " + type);
    }
}

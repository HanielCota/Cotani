package com.cotani.mail.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Upgrades development schemas that originally created the body as a short VARCHAR column. */
public final class MigrateMailBodyTextMigration implements Migration {
    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Upgrade Cotani mail body column to text";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return switch (schema.dialect().name()) {
            case "mysql", "mariadb" ->
                schema.execute("ALTER TABLE cotani_mail_messages MODIFY COLUMN body TEXT NOT NULL");
            case "sqlite" -> CompletableFuture.completedFuture(null);
            default ->
                CompletableFuture.failedFuture(new IllegalStateException(
                        "Unsupported storage dialect: " + schema.dialect().name()));
        };
    }
}

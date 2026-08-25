package com.cotani.mail.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Creates the SQL table used by {@link StorageMailRepository}. */
public final class CreateMailTablesMigration implements Migration {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani mail table";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_mail_messages")
                .id("message_id", ColumnType.STRING)
                .required("sender_id", ColumnType.UUID)
                .required("recipient_id", ColumnType.UUID)
                .required("subject", ColumnType.STRING)
                .required("body", ColumnType.TEXT)
                .required("sent_at", ColumnType.TIMESTAMP)
                .required("expires_at", ColumnType.TIMESTAMP)
                .required("is_read", ColumnType.BOOLEAN)
                .createIfNotExists();
    }
}

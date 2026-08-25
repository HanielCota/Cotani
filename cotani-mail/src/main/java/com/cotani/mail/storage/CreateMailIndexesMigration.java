package com.cotani.mail.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

/** Adds indexes for inbox pagination, unread counts and expiration cleanup. */
public final class CreateMailIndexesMigration implements Migration {
    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Create Cotani mail indexes";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.execute("CREATE INDEX IF NOT EXISTS cotani_mail_recipient_sent_idx "
                        + "ON cotani_mail_messages (recipient_id, sent_at DESC, message_id DESC)")
                .thenCompose(ignored -> schema.execute(
                        "CREATE INDEX IF NOT EXISTS cotani_mail_expiry_idx " + "ON cotani_mail_messages (expires_at)"));
    }
}

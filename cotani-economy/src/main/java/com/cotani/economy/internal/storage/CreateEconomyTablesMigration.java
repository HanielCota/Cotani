package com.cotani.economy.internal.storage;

import com.cotani.storage.migration.Migration;
import com.cotani.storage.schema.ColumnType;
import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

public final class CreateEconomyTablesMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Create Cotani economy accounts and transactions tables";
    }

    @Override
    public CompletionStage<Void> migrate(Schema schema) {
        return schema.table("cotani_economy_accounts")
                .id("user_id", ColumnType.UUID)
                .required("currency_id", ColumnType.STRING)
                .required("balance", ColumnType.STRING)
                .required("created_at", ColumnType.TIMESTAMP)
                .required("updated_at", ColumnType.TIMESTAMP)
                .createIfNotExists()
                .thenCompose(_ -> schema.table("cotani_economy_transactions")
                        .id("transaction_id", ColumnType.UUID)
                        .required("operation_id", ColumnType.UUID)
                        .required("type", ColumnType.STRING)
                        .column("source_user_id", ColumnType.UUID)
                        .column("target_user_id", ColumnType.UUID)
                        .required("currency_id", ColumnType.STRING)
                        .required("amount", ColumnType.STRING)
                        .column("source_balance_before", ColumnType.STRING)
                        .column("source_balance_after", ColumnType.STRING)
                        .column("target_balance_before", ColumnType.STRING)
                        .column("target_balance_after", ColumnType.STRING)
                        .required("reason_key", ColumnType.STRING)
                        .required("reason_source", ColumnType.STRING)
                        .column("reason_actor_user_id", ColumnType.UUID)
                        .required("created_at", ColumnType.TIMESTAMP)
                        .createIfNotExists());
    }
}

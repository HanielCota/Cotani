package com.cotani.storage.migration;

import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

@SuppressWarnings("NullableProblems")
public interface Migration {

    int version();

    String description();

    CompletionStage<Void> migrate(Schema schema);
}

package com.cotani.storage.migration;

import com.cotani.storage.schema.Schema;
import java.util.concurrent.CompletionStage;

@SuppressWarnings("NullableProblems")
public interface Migration {

    /** Namespace in which versions are ordered; defaults to the migration package. */
    default String namespace() {
        return getClass().getPackageName();
    }

    int version();

    String description();

    CompletionStage<Void> migrate(Schema schema);
}

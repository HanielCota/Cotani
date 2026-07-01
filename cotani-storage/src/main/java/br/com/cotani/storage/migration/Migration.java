package br.com.cotani.storage.migration;

import br.com.cotani.storage.future.StorageFuture;
import br.com.cotani.storage.schema.Schema;

public interface Migration {

    int version();

    String description();

    StorageFuture<Void> migrate(Schema schema);
}

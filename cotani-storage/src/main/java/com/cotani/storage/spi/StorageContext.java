package com.cotani.storage.spi;

import com.cotani.storage.dialect.SqlDialect;
import com.cotani.storage.query.TableQuery;
import com.cotani.storage.schema.Schema;
import com.cotani.storage.transaction.TransactionManager;

/**
 * Narrow storage port available to repository implementations.
 *
 * <p>Repositories depend on this SPI instead of the lifecycle facade, which keeps construction,
 * shutdown and backend selection out of repository code.
 */
public interface StorageContext {

    TableQuery table(String name);

    Schema schema();

    SqlDialect dialect();

    TransactionManager transactions();
}

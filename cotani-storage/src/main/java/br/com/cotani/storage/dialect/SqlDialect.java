package br.com.cotani.storage.dialect;

import java.util.List;

public interface SqlDialect {

    String name();

    String autoIncrement();

    String currentTimestamp();

    String type(String logicalType, int length);

    String upsert(String table, List<String> insertColumns, List<String> conflictColumns, List<String> updateColumns);
}

package com.cotani.storage.query;

import java.sql.SQLException;

@FunctionalInterface
public interface EntityMapper<T> {
    T map(Row row) throws SQLException;
}

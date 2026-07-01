package br.com.cotani.storage.query;

import java.sql.SQLException;

@FunctionalInterface
public interface SqlConsumer<T> {
    void accept(T value) throws SQLException;
}

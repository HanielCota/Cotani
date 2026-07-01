package br.com.cotani.storage.provider;

import java.sql.Connection;
import java.sql.SQLException;

public interface StorageProvider extends AutoCloseable {

    void start();

    Connection connection() throws SQLException;

    boolean available();

    @Override
    void close();
}

package br.com.cotani.storage.transaction;

import java.sql.Connection;

public record TransactionContext(Connection connection) {}

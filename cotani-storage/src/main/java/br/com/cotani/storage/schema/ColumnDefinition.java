package br.com.cotani.storage.schema;

record ColumnDefinition(String name, ColumnType type, int length, boolean primary, boolean nullable, boolean unique) {}

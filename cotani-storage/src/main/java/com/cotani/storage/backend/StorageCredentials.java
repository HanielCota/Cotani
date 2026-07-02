package com.cotani.storage.backend;

public sealed interface StorageCredentials permits MySqlCredentials, MariaDbCredentials, SQLiteCredentials {}

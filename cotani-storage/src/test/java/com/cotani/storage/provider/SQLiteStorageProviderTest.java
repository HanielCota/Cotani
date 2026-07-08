package com.cotani.storage.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.storage.backend.SQLiteCredentials;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SQLiteStorageProviderTest {

    @TempDir
    Path tempDir;

    private SQLiteStorageProvider provider;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("test.db");
        var credentials = new SQLiteCredentials(dbFile);
        provider = new SQLiteStorageProvider(credentials);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    @Test
    void testConnectionIsNotClosedByCallerCloseCall() throws Exception {
        provider.start();
        assertTrue(provider.available());

        // Get connection
        Connection conn1 = provider.connection();
        assertNotNull(conn1);
        assertFalse(conn1.isClosed());

        // Run query
        try (Statement stmt = conn1.createStatement()) {
            stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, val TEXT)");
            stmt.execute("INSERT INTO test (val) VALUES ('hello')");
        }

        // Simulate QueryExecutor ConnectionScope close
        conn1.close(); // proxy close should be no-op

        // Try getting connection again and query
        Connection conn2 = provider.connection();
        assertNotNull(conn2);
        assertFalse(conn2.isClosed());

        try (Statement stmt = conn2.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT val FROM test")) {
            assertTrue(rs.next());
            assertEquals("hello", rs.getString("val"));
        }
    }

    @Test
    void testCloseReallyClosesConnection() throws Exception {
        provider.start();
        assertNotNull(provider.connection());

        provider.close();
        assertFalse(provider.available());
        assertThrows(Exception.class, provider::connection);
    }
}

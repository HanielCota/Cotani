package com.cotani.cooldown.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cotani.cooldown.CotaniCooldowns;
import com.cotani.storage.migration.Migration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CooldownMigrationsTest {
    @Test
    void shouldCreateTableMigrationExposeMetadata() {
        var migration = new CreateCooldownsTableMigration();

        assertEquals(1, migration.version());
        assertEquals("Create Cotani cooldowns table", migration.description());
    }

    @Test
    void shouldAddLeaseTokenMigrationExposeMetadata() {
        var migration = new AddCooldownLeaseTokenMigration();

        assertEquals(2, migration.version());
        assertEquals("Add distributed cooldown lease token", migration.description());
    }

    @Test
    void shouldRegisterMigrationsInCreationOrder() {
        var versions =
                CotaniCooldowns.migrations().stream().map(Migration::version).toList();

        assertEquals(List.of(1, 2), versions);
    }
}

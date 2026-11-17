package dev.autowatcher.monitoring;

import java.nio.file.*;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MigrationUpgradeTest {
    @TempDir Path directory;
    @Test void upgradingExistingV2DatabasePreservesUserProfile() throws Exception {
        String url="jdbc:sqlite:"+directory.resolve("upgrade.db");
        Flyway.configure().dataSource(url,null,null).target("2").load().migrate();
        try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO search_profiles(id,name,enabled,sources,created_at,updated_at)
                VALUES('existing','Saved profile',1,'AVITO','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')
                """);
        }
        var flyway=Flyway.configure().dataSource(url,null,null).load();
        assertEquals(1,flyway.migrate().migrationsExecuted);
        flyway.validate();
        try(var connection=DriverManager.getConnection(url);var statement=connection.createStatement();
                var rows=statement.executeQuery("SELECT name FROM search_profiles WHERE id='existing'")) {
            assertTrue(rows.next());assertEquals("Saved profile",rows.getString(1));
        }
    }
}

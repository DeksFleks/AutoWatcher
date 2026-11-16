package dev.autowatcher.monitoring;

import dev.autowatcher.listing.domain.CarListing;
import dev.autowatcher.monitoring.application.*;
import dev.autowatcher.notification.TestOutboxDispatcher;
import dev.autowatcher.search.application.*;
import dev.autowatcher.search.domain.*;
import dev.autowatcher.source.fake.FakeListingSource;
import dev.autowatcher.source.api.SourceQuery;
import java.net.URI;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("demo")
class MonitoringCycleTest {
    private static final Path DATABASE = Path.of(System.getProperty("java.io.tmpdir"),
        "autowatcher-test-" + UUID.randomUUID() + ".db");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }
    @Autowired SearchProfileService profiles;
    @Autowired MonitoringOrchestrator monitoring;
    @Autowired ListingIngestionService ingestion;
    @Autowired ListingRetentionService retention;
    @Autowired TestOutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() {
        for (String table : List.of("test_notification_receipts", "notification_outbox",
                "listing_matches", "profile_listings", "monitoring_runs", "listings", "search_profiles"))
            jdbc.update("DELETE FROM " + table);
    }

    @Test void fullCycleSurvivesRepeatedChecksAndDeliversOnce() throws Exception {
        SearchProfile profile = profile();
        var runs = monitoring.check(profile.id());
        assertEquals(1, runs.size());
        assertEquals("SUCCEEDED", runs.getFirst().status());
        assertEquals(4, count("listings")); // two fake pages were consumed
        assertEquals(1, countWhere("listing_matches", "result='MATCH'"));
        assertEquals(1, countWhere("listing_matches", "result='UNKNOWN'"));
        assertEquals(2, countWhere("listing_matches", "result='NO_MATCH'"));
        assertEquals(1, count("notification_outbox"));
        String id = jdbc.queryForObject(
            "SELECT id FROM listings WHERE external_id='bmw-match'", String.class);
        String firstSeen = jdbc.queryForObject(
            "SELECT first_seen_at FROM listings WHERE id=?", String.class, id);

        monitoring.check(profile.id());
        assertEquals(4, count("listings"));
        assertEquals(firstSeen, jdbc.queryForObject(
            "SELECT first_seen_at FROM listings WHERE id=?", String.class, id));
        assertEquals(1, count("notification_outbox"));
        assertEquals(1, dispatcher.dispatch(profile.id()));
        assertEquals(0, dispatcher.dispatch(profile.id()));
        assertEquals(1, count("test_notification_receipts"));
        assertEquals(1, countWhere("notification_outbox", "status='DELIVERED' AND attempts=1"));

        // File-backed SQLite is readable from a fresh independent JDBC connection.
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM notification_outbox")) {
            assertTrue(result.next());
            assertEquals(1, result.getInt(1));
        }
        assertEquals(3, jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND type='SQL'", Integer.class));

        retention.compactBefore(Instant.now());
        assertEquals("Archived listing", jdbc.queryForObject(
            "SELECT title FROM listings WHERE id=?", String.class, id));
        monitoring.check(profile.id());
        assertEquals(1, count("notification_outbox"));
        assertEquals(1, count("test_notification_receipts"));
        profiles.delete(profile.id());
        assertEquals(0, count("listing_matches"));
        assertEquals(0, count("notification_outbox"));
        assertEquals(0, count("monitoring_runs"));
    }

    @Test void ingestionRollsBackListingMatchAndOutboxTogether() {
        SearchProfile profile = profile();
        CarListing valid = new FakeListingSource()
            .search(new SourceQuery(profile.criteria(), 1, null)).listings().getFirst();
        CarListing invalid = new CarListing(SourceType.FAKE, null, URI.create("https://example.invalid"),
            "Invalid", null, null, null, null, null, null, Instant.now());
        assertThrows(IllegalArgumentException.class, () -> ingestion.ingest(profile, List.of(valid, invalid)));
        assertEquals(0, count("listings"));
        assertEquals(0, count("listing_matches"));
        assertEquals(0, count("profile_listings"));
        assertEquals(0, count("notification_outbox"));
    }

    @Test void changedListingIsUpdatedAndUnknownCanBecomeMatch() {
        SearchProfile profile = profile();
        var unknown = new CarListing(SourceType.FAKE, "changing", URI.create("https://example.invalid/changing"),
            "BMW", "BMW", "5 Series", 2021, null, 65000, "Москва", Instant.now());
        ingestion.ingest(profile, List.of(unknown));
        assertEquals(0, count("notification_outbox"));
        var updated = new CarListing(unknown.source(), unknown.externalId(), unknown.url(), unknown.title(),
            unknown.brand(), unknown.model(), unknown.year(), 2500000L, unknown.mileage(),
            unknown.region(), Instant.now());
        ingestion.ingest(profile, List.of(updated));
        assertEquals(1, count("listings"));
        assertEquals(2500000L, jdbc.queryForObject("SELECT price FROM listings", Long.class));
        assertEquals(1, count("notification_outbox"));
    }

    @Test void disabledProfileDoesNotStartRun() {
        var profile = profile();
        profiles.setEnabled(profile.id(), false);
        assertThrows(IllegalArgumentException.class, () -> monitoring.check(profile.id()));
        assertEquals(0, count("monitoring_runs"));
    }

    @Test void sourceFailureIsRecorded() {
        var profile = profiles.create(new CreateSearchProfileCommand("Unavailable",
            criteria(), Set.of(SourceType.AVITO)));
        var runs = monitoring.check(profile.id());
        assertEquals("FAILED", runs.getFirst().status());
        assertNotNull(runs.getFirst().finishedAt());
        assertEquals(0, count("notification_outbox"));
    }

    private SearchProfile profile() {
        return profiles.create(new CreateSearchProfileCommand("BMW demo", criteria(), Set.of(SourceType.FAKE)));
    }
    private SearchCriteria criteria() {
        return new SearchCriteria("BMW", "5 Series", 2019, 2023, null, 3000000L, 100000, "Москва");
    }
    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
    private int countWhere(String table, String condition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + condition, Integer.class);
    }
}

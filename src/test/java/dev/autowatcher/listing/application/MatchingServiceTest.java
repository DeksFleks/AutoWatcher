package dev.autowatcher.listing.application;

import dev.autowatcher.listing.domain.*;
import dev.autowatcher.search.domain.*;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchingServiceTest {
    private final MatchingService service = new MatchingService();
    @Test void matchesListing() {
        var criteria = new SearchCriteria("BMW", "5 Series", 2019, 2023, null, 3000000L, 100000, null);
        var listing = new CarListing(SourceType.AVITO, "123", URI.create("https://example.com/123"),
            "BMW 5 Series", "BMW", "5 Series", 2021, 2500000L, 65000, "Москва", Instant.now());
        assertEquals(MatchResult.MATCH, service.match(criteria, listing));
    }
    @Test void missingRequiredValueIsUnknown() {
        var criteria = new SearchCriteria("BMW", null, null, null, null, 3000000L, null, null);
        var listing = new CarListing(SourceType.AVITO, "123", URI.create("https://example.com/123"),
            "BMW", "BMW", null, null, null, null, null, Instant.now());
        assertEquals(MatchResult.UNKNOWN, service.match(criteria, listing));
    }
}

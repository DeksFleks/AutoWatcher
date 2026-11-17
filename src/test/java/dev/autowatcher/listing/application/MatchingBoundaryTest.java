package dev.autowatcher.listing.application;

import dev.autowatcher.listing.domain.*;
import dev.autowatcher.search.domain.*;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchingBoundaryTest {
    private final MatchingService service = new MatchingService();
    private final SearchCriteria criteria = new SearchCriteria("BMW", "5 Series",
        2019, 2023, 1000000L, 3000000L, 100000, "Москва");

    @Test void inclusiveNumericBoundaries() {
        assertEquals(MatchResult.MATCH, service.match(criteria, listing(2019, 1000000L, 100000, "BMW")));
        assertEquals(MatchResult.MATCH, service.match(criteria, listing(2023, 3000000L, 0, "bmw")));
        assertEquals(MatchResult.NO_MATCH, service.match(criteria, listing(2018, 1000000L, 100000, "BMW")));
        assertEquals(MatchResult.NO_MATCH, service.match(criteria, listing(2024, 1000000L, 100000, "BMW")));
        assertEquals(MatchResult.NO_MATCH, service.match(criteria, listing(2021, 999999L, 100000, "BMW")));
        assertEquals(MatchResult.NO_MATCH, service.match(criteria, listing(2021, 3000001L, 100000, "BMW")));
        assertEquals(MatchResult.NO_MATCH, service.match(criteria, listing(2021, 2000000L, 100001, "BMW")));
    }
    @Test void unknownFieldsNeverProducePositiveMatch() {
        assertEquals(MatchResult.UNKNOWN, service.match(criteria, listing(null, 2000000L, 0, "BMW")));
        assertEquals(MatchResult.UNKNOWN, service.match(criteria, listing(2021, null, 0, "BMW")));
        assertEquals(MatchResult.UNKNOWN, service.match(criteria, listing(2021, 2000000L, null, "BMW")));
        assertEquals(MatchResult.UNKNOWN, service.match(criteria, listing(2021, 2000000L, 0, " ")));
    }
    @Test void definiteMismatchTakesPrecedenceOverUnknown() {
        assertEquals(MatchResult.NO_MATCH, service.match(criteria, listing(null, 4000000L, null, "BMW")));
        assertEquals(MatchResult.NO_MATCH, service.match(criteria, listing(null, null, null, "Toyota")));
    }
    @Test void absentConstraintsAcceptMissingValues() {
        var empty = new SearchCriteria(null, null, null, null, null, null, null, null);
        assertEquals(MatchResult.MATCH, service.match(empty, listing(null, null, null, null)));
    }
    private CarListing listing(Integer year, Long price, Integer mileage, String brand) {
        return new CarListing(SourceType.FAKE, "1", URI.create("https://example.invalid/1"),
            "Fixture", brand, "5 Series", year, price, mileage, "Москва", Instant.now());
    }
}

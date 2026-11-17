package dev.autowatcher.source.fake;

import dev.autowatcher.listing.domain.CarListing;
import dev.autowatcher.search.domain.SourceType;
import dev.autowatcher.source.api.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Deterministic, paginated fixture; never makes network requests. */
@Component
@Profile("demo")
public class FakeListingSource implements ListingSource {
    @Override public SourceType type() { return SourceType.FAKE; }
    @Override public SourceCapabilities capabilities() {
        return new SourceCapabilities(Set.of());
    }
    @Override public SearchPage search(SourceQuery query) {
        if (query.limit() < 1 || query.limit() > 100)
            throw new IllegalArgumentException("limit must be between 1 and 100");
        int offset = query.cursor() == null ? 0 : Integer.parseInt(query.cursor());
        Instant now = Instant.now();
        List<CarListing> fixtures = List.of(
            listing("bmw-match", "BMW", "5 Series", 2021, 2500000L, 65000, now),
            listing("bmw-expensive", "BMW", "5 Series", 2022, 4500000L, 40000, now),
            listing("bmw-unknown", "BMW", "5 Series", 2020, null, 80000, now),
            listing("toyota-match", "Toyota", "Camry", 2020, 2400000L, 70000, now));
        if (offset < 0 || offset > fixtures.size())
            throw new IllegalArgumentException("Invalid fake source cursor");
        int end = Math.min(offset + query.limit(), fixtures.size());
        return new SearchPage(fixtures.subList(offset, end),
            end < fixtures.size() ? Integer.toString(end) : null);
    }
    private CarListing listing(String id, String brand, String model, Integer year,
                               Long price, Integer mileage, Instant now) {
        return new CarListing(type(), id, URI.create("https://example.invalid/cars/" + id),
            brand + " " + model, brand, model, year, price, mileage, "Москва", now);
    }
}

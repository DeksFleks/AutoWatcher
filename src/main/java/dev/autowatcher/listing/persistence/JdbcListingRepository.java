package dev.autowatcher.listing.persistence;

import dev.autowatcher.listing.domain.CarListing;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcListingRepository implements ListingRepository {
    private final JdbcTemplate jdbc;
    public JdbcListingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public UUID upsert(CarListing l) {
        if (l == null || l.source() == null || l.externalId() == null || l.externalId().isBlank()
                || l.url() == null || l.title() == null || l.observedAt() == null)
            throw new IllegalArgumentException("Listing identity, URL, title and observedAt are required");
        jdbc.update("""
            INSERT INTO listings(id,source,external_id,url,title,brand,model,year,price,mileage,
                region,first_seen_at,last_seen_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(source,external_id) DO UPDATE SET
                url=excluded.url,title=excluded.title,brand=excluded.brand,model=excluded.model,
                year=excluded.year,price=excluded.price,mileage=excluded.mileage,
                region=excluded.region,last_seen_at=excluded.last_seen_at
            """, UUID.randomUUID().toString(), l.source().name(), l.externalId(), l.url().toString(),
            l.title(), l.brand(), l.model(), l.year(), l.price(), l.mileage(), l.region(),
            l.observedAt().toString(), l.observedAt().toString());
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id FROM listings WHERE source=? AND external_id=?", String.class,
            l.source().name(), l.externalId()));
    }
}

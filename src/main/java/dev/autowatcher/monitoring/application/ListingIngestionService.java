package dev.autowatcher.monitoring.application;

import dev.autowatcher.listing.application.MatchingService;
import dev.autowatcher.listing.domain.*;
import dev.autowatcher.listing.persistence.ListingRepository;
import dev.autowatcher.search.domain.SearchProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingIngestionService {
    private final ListingRepository listings;
    private final MatchingService matching;
    private final JdbcTemplate jdbc;

    public ListingIngestionService(ListingRepository listings, MatchingService matching, JdbcTemplate jdbc) {
        this.listings = listings;
        this.matching = matching;
        this.jdbc = jdbc;
    }

    /** A page, its matches and its outbox events commit together or roll back together. */
    @Transactional
    public int ingest(SearchProfile profile, List<CarListing> page) {
        int queued = 0;
        String now = Instant.now().toString();
        for (CarListing listing : page) {
            UUID id = listings.upsert(listing);
            MatchResult result = matching.match(profile.criteria(), listing);
            jdbc.update("""
                INSERT INTO listing_matches(profile_id,listing_id,result,checked_at) VALUES(?,?,?,?)
                ON CONFLICT(profile_id,listing_id) DO UPDATE SET
                    result=excluded.result,checked_at=excluded.checked_at
                """, profile.id().toString(), id.toString(), result.name(), now);
            if (result != MatchResult.MATCH) continue;
            int firstMatch = jdbc.update("""
                INSERT INTO profile_listings(profile_id,listing_id,first_matched_at) VALUES(?,?,?)
                ON CONFLICT(profile_id,listing_id) DO NOTHING
                """, profile.id().toString(), id.toString(), now);
            if (firstMatch == 1) {
                queued += jdbc.update("""
                    INSERT INTO notification_outbox(id,profile_id,listing_id,channel,status,next_attempt_at)
                    VALUES(?,?,?,?,'PENDING',?)
                    ON CONFLICT(profile_id,listing_id,channel) DO NOTHING
                    """, UUID.randomUUID().toString(), profile.id().toString(), id.toString(),
                    listing.source() == dev.autowatcher.search.domain.SourceType.FAKE ? "TEST" : "TELEGRAM", now);
            }
        }
        return queued;
    }
}

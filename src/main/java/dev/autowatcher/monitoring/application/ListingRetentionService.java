package dev.autowatcher.monitoring.application;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingRetentionService {
    private final JdbcTemplate jdbc;
    public ListingRetentionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Drop obsolete detail fields, retaining source identity, match ledger and outbox
     * uniqueness. A later source observation can restore details without notifying twice.
     */
    @Transactional
    public int compactBefore(Instant cutoff) {
        if (cutoff == null || cutoff.isAfter(Instant.now()))
            throw new IllegalArgumentException("Retention cutoff must not be in the future");
        return jdbc.update("""
            UPDATE listings SET url='https://example.invalid/expired',title='Archived listing',
                brand=NULL,model=NULL,year=NULL,price=NULL,mileage=NULL,region=NULL
            WHERE last_seen_at<? AND title<>'Archived listing'
            AND NOT EXISTS (
                SELECT 1 FROM notification_outbox o
                WHERE o.listing_id=listings.id AND o.status<>'DELIVERED'
            )
            """, cutoff.toString());
    }
}

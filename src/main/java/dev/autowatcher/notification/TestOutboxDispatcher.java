package dev.autowatcher.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local test transport only. Receipt and acknowledgement share the same transaction. */
@Service
public class TestOutboxDispatcher {
    private final JdbcTemplate jdbc;
    public TestOutboxDispatcher(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public int dispatch(UUID profileId) {
        String now = Instant.now().toString();
        List<Event> events = jdbc.query("""
            SELECT id,profile_id,listing_id FROM notification_outbox
            WHERE profile_id=? AND channel='TEST' AND status='PENDING' AND next_attempt_at<=?
            ORDER BY next_attempt_at,id LIMIT 100
            """, (rs, n) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3)),
            profileId.toString(), now);
        for (Event event : events) {
            jdbc.update("""
                INSERT INTO test_notification_receipts(outbox_id,profile_id,listing_id,received_at)
                VALUES(?,?,?,?) ON CONFLICT(outbox_id) DO NOTHING
                """, event.id(), event.profileId(), event.listingId(), now);
            jdbc.update("""
                UPDATE notification_outbox SET status='DELIVERED',delivered_at=?,attempts=attempts+1
                WHERE id=? AND status='PENDING'
                """, now, event.id());
        }
        return events.size();
    }
    private record Event(String id, String profileId, String listingId) {}
}

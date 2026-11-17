package dev.autowatcher.monitoring.application;

import dev.autowatcher.search.application.SearchProfileService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MonitoringQueryService {
    private final JdbcTemplate jdbc;
    private final SearchProfileService profiles;
    public MonitoringQueryService(JdbcTemplate jdbc, SearchProfileService profiles) {
        this.jdbc = jdbc;
        this.profiles = profiles;
    }
    public List<Map<String, Object>> runs(UUID id) {
        profiles.findById(id);
        return jdbc.queryForList("""
            SELECT * FROM monitoring_runs WHERE profile_id=? ORDER BY started_at DESC LIMIT 100
            """, id.toString());
    }
    public List<Map<String, Object>> results(UUID id) {
        return results(id, null);
    }
    public List<Map<String, Object>> results(UUID id, dev.autowatcher.listing.domain.MatchResult result) {
        profiles.findById(id);
        return jdbc.queryForList("""
            SELECT l.*,m.result,m.checked_at FROM listing_matches m
            JOIN listings l ON l.id=m.listing_id WHERE m.profile_id=?
            AND (? IS NULL OR m.result=?)
            ORDER BY m.checked_at DESC,l.id LIMIT 200
            """, id.toString(),result==null?null:result.name(),result==null?null:result.name());
    }
    public List<Map<String, Object>> outbox(UUID id) {
        profiles.findById(id);
        return jdbc.queryForList("""
            SELECT o.*,r.received_at AS test_received_at FROM notification_outbox o
            LEFT JOIN test_notification_receipts r ON r.outbox_id=o.id
            WHERE o.profile_id=? ORDER BY o.next_attempt_at DESC,o.id LIMIT 200
            """, id.toString());
    }
}

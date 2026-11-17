package dev.autowatcher.monitoring.application;

import dev.autowatcher.settings.SettingsService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.background.enabled", havingValue="true", matchIfMissing=true)
public class MonitoringScheduler {
    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);
    private final JdbcTemplate jdbc;
    private final MonitoringOrchestrator monitoring;
    private final ListingRetentionService retention;
    private final SettingsService settings;
    public MonitoringScheduler(JdbcTemplate jdbc, MonitoringOrchestrator monitoring,
            ListingRetentionService retention, SettingsService settings) {
        this.jdbc=jdbc; this.monitoring=monitoring; this.retention=retention; this.settings=settings;
    }
    @Scheduled(fixedDelay=2000, initialDelay=5000)
    public void tick() {
        var ids = jdbc.query("""
            SELECT p.id FROM search_profiles p WHERE p.enabled=1 AND (
                EXISTS(SELECT 1 FROM requested_checks q WHERE q.profile_id=p.id) OR
                EXISTS(SELECT 1 FROM profile_monitoring m WHERE m.profile_id=p.id
                    AND m.scheduled=1 AND m.next_check_at<=?)) LIMIT 5
            """, (rs,n) -> UUID.fromString(rs.getString(1)), Instant.now().toString());
        for (UUID id : ids) {
            // Delete before executing: a newly requested check during execution is preserved.
            jdbc.update("DELETE FROM requested_checks WHERE profile_id=?",id.toString());
            try { monitoring.check(id); }
            catch (RuntimeException e) { log.warn("Scheduled check {} failed",id); }
            finally {
                jdbc.update("""
                    UPDATE profile_monitoring SET next_check_at=? WHERE profile_id=?
                    """, Instant.now().plusSeconds(interval(id)).toString(),id.toString());
            }
        }
    }
    private int interval(UUID id) {
        return jdbc.query("SELECT interval_seconds FROM profile_monitoring WHERE profile_id=?",
            (rs,n)->rs.getInt(1),id.toString()).stream().findFirst().orElse(300);
    }
    @Scheduled(fixedDelay=86400000, initialDelay=60000)
    public void compact() {
        int days = Integer.parseInt(settings.get("retention_days","30"));
        retention.compactBefore(Instant.now().minus(days,ChronoUnit.DAYS));
        jdbc.update("DELETE FROM monitoring_runs WHERE finished_at<? AND status<>'RUNNING'",
            Instant.now().minus(days,ChronoUnit.DAYS).toString());
    }
}

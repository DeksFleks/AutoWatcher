package dev.autowatcher.monitoring.application;

import java.time.Instant;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MonitoringRecovery {
    private final JdbcTemplate jdbc;
    public MonitoringRecovery(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        jdbc.update("""
            UPDATE monitoring_runs SET status='INTERRUPTED',finished_at=?,
                error_message='Application stopped before this check finished'
            WHERE status='RUNNING'
            """, Instant.now().toString());
    }
}

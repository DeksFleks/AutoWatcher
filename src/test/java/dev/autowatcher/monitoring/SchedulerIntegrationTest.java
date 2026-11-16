package dev.autowatcher.monitoring;

import dev.autowatcher.monitoring.application.*;
import dev.autowatcher.search.application.*;
import dev.autowatcher.search.domain.*;
import dev.autowatcher.settings.SettingsService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:sqlite::memory:")
@ActiveProfiles("demo")
class SchedulerIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchProfileService profiles;
    @Autowired ProfileMonitoringService configuration;
    @Autowired MonitoringOrchestrator monitoring;
    @Autowired ListingRetentionService retention;
    @Autowired SettingsService settings;
    @Test void dueScheduleRunsOnceThenAdvancesWhileManualQueueStillWorks() {
        var profile=profiles.create(new CreateSearchProfileCommand("schedule",new SearchCriteria("BMW",null,null,null,null,3000000L,null,null),Set.of(SourceType.FAKE)));
        configuration.save(profile.id(),new ProfileMonitoringService.Configuration(300,true,null,null,null));
        var scheduler=new MonitoringScheduler(jdbc,monitoring,retention,settings);
        scheduler.tick();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_runs",Integer.class));
        assertTrue(Instant.parse(configuration.get(profile.id()).nextCheckAt()).isAfter(Instant.now()));
        scheduler.tick();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_runs",Integer.class));
        configuration.requestCheck(profile.id());configuration.requestCheck(profile.id());
        scheduler.tick();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_runs",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM requested_checks",Integer.class));
        profiles.setEnabled(profile.id(),false);
        jdbc.update("UPDATE profile_monitoring SET next_check_at=?",Instant.EPOCH.toString());
        scheduler.tick();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_runs",Integer.class));
        profiles.delete(profile.id());
    }
}

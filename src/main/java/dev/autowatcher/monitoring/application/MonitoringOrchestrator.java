package dev.autowatcher.monitoring.application;

import dev.autowatcher.monitoring.domain.MonitoringRun;
import dev.autowatcher.search.application.SearchProfileService;
import dev.autowatcher.search.domain.*;
import dev.autowatcher.source.api.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MonitoringOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(MonitoringOrchestrator.class);
    private final SearchProfileService profiles;
    private final List<ListingSource> sources;
    private final ListingIngestionService ingestion;
    private final JdbcTemplate jdbc;
    private final ProfileMonitoringService configuration;

    public MonitoringOrchestrator(SearchProfileService profiles, List<ListingSource> sources,
                                  ListingIngestionService ingestion, JdbcTemplate jdbc, ProfileMonitoringService configuration) {
        this.profiles = profiles;
        this.sources = sources;
        this.ingestion = ingestion;
        this.jdbc = jdbc;
        this.configuration = configuration;
    }

    // One local process, one writer. No database transaction is held during source I/O.
    public synchronized List<MonitoringRun> check(UUID profileId) {
        SearchProfile profile = profiles.findById(profileId);
        if (!profile.enabled()) throw new IllegalArgumentException("Profile is disabled");
        List<MonitoringRun> runs = new ArrayList<>();
        for (SourceType type : profile.sources().stream().sorted().toList()) {
            UUID runId = UUID.randomUUID();
            Instant started = Instant.now();
            jdbc.update("""
                INSERT INTO monitoring_runs(id,profile_id,source,status,started_at)
                VALUES(?,?,?,'RUNNING',?)
                """, runId.toString(), profileId.toString(), type.name(), started.toString());
            String status = "SUCCEEDED";
            String error = null;
            try {
                ListingSource source = sources.stream().filter(s -> s.type() == type).findFirst()
                    .orElseThrow(() -> new SourceException("Source is unavailable in this configuration"));
                String cursor = null;
                Set<String> cursors = new HashSet<>();
                for (int pageNumber = 0; ; pageNumber++) {
                    if (pageNumber >= 10) throw new SourceException("Source page limit exceeded");
                    SearchPage page = source.search(new SourceQuery(profile.criteria(), type == SourceType.FAKE ? 2 : 50, cursor, configuration.searchUrl(profileId, type)));
                    if (page == null || page.listings() == null)
                        throw new SourceException("Source returned an invalid page");
                    if (page.listings().stream().anyMatch(l -> l == null || l.source() != type))
                        throw new SourceException("Source returned a listing with an invalid identity");
                    ingestion.ingest(profile, page.listings());
                    cursor = page.nextCursor();
                    if (cursor == null) break;
                    if (!cursors.add(cursor)) throw new SourceException("Source repeated its cursor");
                }
            } catch (RuntimeException e) {
                status = "FAILED";
                error = e instanceof SourceException || e instanceof IllegalArgumentException
                    ? e.getMessage() : "Ошибка проверки; см. журнал приложения";
                log.warn("Monitoring run {} failed for source {}: {}", runId, type, error);
            }
            Instant finished = Instant.now();
            jdbc.update("UPDATE monitoring_runs SET status=?,finished_at=?,error_message=? WHERE id=?",
                status, finished.toString(), error, runId.toString());
            runs.add(new MonitoringRun(runId, profileId, type, status, started, finished, error));
        }
        return List.copyOf(runs);
    }
}

package dev.autowatcher.monitoring.web;

import dev.autowatcher.monitoring.application.*;
import dev.autowatcher.monitoring.domain.MonitoringRun;
import dev.autowatcher.notification.TestOutboxDispatcher;
import dev.autowatcher.search.application.SearchProfileService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profiles/{id}")
public class MonitoringController {
    private final MonitoringOrchestrator monitoring;
    private final MonitoringQueryService queries;
    private final TestOutboxDispatcher dispatcher;
    private final SearchProfileService profiles;
    public MonitoringController(MonitoringOrchestrator monitoring, MonitoringQueryService queries,
                                TestOutboxDispatcher dispatcher, SearchProfileService profiles) {
        this.monitoring = monitoring;
        this.queries = queries;
        this.dispatcher = dispatcher;
        this.profiles = profiles;
    }
    @PostMapping("/checks")
    public List<MonitoringRun> check(@PathVariable UUID id) { return monitoring.check(id); }
    @GetMapping("/runs")
    public List<Map<String, Object>> runs(@PathVariable UUID id) { return queries.runs(id); }
    @GetMapping("/results")
    public List<Map<String, Object>> results(@PathVariable UUID id,
        @RequestParam(required=false) dev.autowatcher.listing.domain.MatchResult result) { return queries.results(id,result); }
    @GetMapping("/outbox")
    public List<Map<String, Object>> outbox(@PathVariable UUID id) { return queries.outbox(id); }
    @PostMapping("/outbox/dispatch-test")
    public Map<String, Integer> dispatch(@PathVariable UUID id) {
        profiles.findById(id);
        return Map.of("deliveredToTestTransport", dispatcher.dispatch(id));
    }
}

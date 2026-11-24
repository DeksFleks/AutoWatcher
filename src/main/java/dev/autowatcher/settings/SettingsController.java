package dev.autowatcher.settings;

import dev.autowatcher.monitoring.application.*;
import dev.autowatcher.notification.TelegramOutboxDispatcher;
import dev.autowatcher.search.application.SearchProfileService;
import dev.autowatcher.shared.web.LocalAccessFilter;
import dev.autowatcher.telegram.TelegramConnectionService;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SettingsController {
    private final SettingsService settings;
    private final TelegramConnectionService telegram;
    private final ProfileMonitoringService monitoring;
    private final SearchProfileService profiles;
    private final TelegramOutboxDispatcher dispatcher;
    private final ObjectProvider<LocalAccessFilter> filter;
    private final Environment environment;
    public SettingsController(SettingsService settings,TelegramConnectionService telegram,
            ProfileMonitoringService monitoring,SearchProfileService profiles,TelegramOutboxDispatcher dispatcher,
            ObjectProvider<LocalAccessFilter> filter,Environment environment) {
        this.settings=settings;this.telegram=telegram;this.monitoring=monitoring;this.profiles=profiles;
        this.dispatcher=dispatcher;this.filter=filter;this.environment=environment;
    }
    @GetMapping("/session")
    public Map<String,Object> session() {
        return Map.of("csrfToken",filter.getIfAvailable()==null?"":filter.getObject().token(),
            "demo",Arrays.asList(environment.getActiveProfiles()).contains("demo"));
    }
    @GetMapping("/settings")
    public Map<String,Object> settings() { return settings.publicSettings(); }
    public record Token(String token) {}
    @PutMapping("/settings/telegram")
    public Map<String,Object> token(@RequestBody Token value) { return telegram.configure(value.token()); }
    @PostMapping("/settings/telegram/pair")
    public Map<String,Object> pair() { return telegram.pair(); }
    @PostMapping("/settings/telegram/test")
    public void testNotification() { dispatcher.sendTest(); }
    @DeleteMapping("/settings/telegram")
    public void disconnect() { settings.disconnectTelegram(); }
    public record Retention(int days) {}
    @PutMapping("/settings/retention")
    public void retention(@RequestBody Retention value) { settings.setRetention(value.days()); }
    @GetMapping("/profiles/{id}/monitoring")
    public ProfileMonitoringService.Configuration config(@PathVariable UUID id) { return monitoring.get(id); }
    @PutMapping("/profiles/{id}/monitoring")
    public ProfileMonitoringService.Configuration config(@PathVariable UUID id,
            @RequestBody ProfileMonitoringService.Configuration value) { return monitoring.save(id,value); }
    @PostMapping("/profiles/{id}/checks/queue")
    public Map<String,String> queue(@PathVariable UUID id) { monitoring.requestCheck(id);return Map.of("status","QUEUED"); }
    @PostMapping("/profiles/{id}/outbox/retry")
    public void retry(@PathVariable UUID id) { profiles.findById(id);dispatcher.retry(id); }
}

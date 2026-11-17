package dev.autowatcher.monitoring.application;

import dev.autowatcher.search.application.SearchProfileService;
import dev.autowatcher.search.domain.SourceType;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileMonitoringService {
    private final JdbcTemplate jdbc;
    private final SearchProfileService profiles;
    public ProfileMonitoringService(JdbcTemplate jdbc, SearchProfileService profiles) {
        this.jdbc = jdbc; this.profiles = profiles;
    }
    public record Configuration(int intervalSeconds, boolean scheduled, String avitoUrl, String autoRuUrl,
                                String nextCheckAt) {}
    public Configuration get(UUID id) {
        profiles.findById(id);
        return jdbc.query("SELECT * FROM profile_monitoring WHERE profile_id=?",
            (rs,n) -> new Configuration(rs.getInt("interval_seconds"), rs.getBoolean("scheduled"),
                rs.getString("avito_url"), rs.getString("auto_ru_url"), rs.getString("next_check_at")),
            id.toString()).stream().findFirst().orElse(new Configuration(300,false,null,null,null));
    }
    @Transactional
    public Configuration save(UUID id, Configuration config) {
        profiles.findById(id);
        if (config.intervalSeconds() < 60 || config.intervalSeconds() > 86400)
            throw new IllegalArgumentException("Interval must be 60..86400 seconds");
        String avito = validateUrl(config.avitoUrl(), SourceType.AVITO);
        String autoRu = validateUrl(config.autoRuUrl(), SourceType.AUTO_RU);
        String next = Instant.now().toString();
        jdbc.update("""
            INSERT INTO profile_monitoring(profile_id,interval_seconds,scheduled,next_check_at,avito_url,auto_ru_url)
            VALUES(?,?,?,?,?,?) ON CONFLICT(profile_id) DO UPDATE SET
            interval_seconds=excluded.interval_seconds,scheduled=excluded.scheduled,
            next_check_at=excluded.next_check_at,avito_url=excluded.avito_url,auto_ru_url=excluded.auto_ru_url
            """, id.toString(),config.intervalSeconds(),config.scheduled(),next,avito,autoRu);
        return get(id);
    }
    public void requestCheck(UUID id) {
        var profile = profiles.findById(id);
        if (!profile.enabled()) throw new IllegalArgumentException("Profile is disabled");
        jdbc.update("""
            INSERT INTO requested_checks(profile_id,requested_at) VALUES(?,?)
            ON CONFLICT(profile_id) DO NOTHING
            """, id.toString(),Instant.now().toString());
    }
    public URI searchUrl(UUID id, SourceType type) {
        if (type == SourceType.FAKE) return null;
        var config = get(id);
        String value = type == SourceType.AVITO ? config.avitoUrl() : config.autoRuUrl();
        if(value==null && type==SourceType.AUTO_RU) {
            var criteria=profiles.findById(id).criteria();
            String brand=criteria.brand()==null?"":criteria.brand().strip().toLowerCase(java.util.Locale.ROOT);
            String model=criteria.model()==null?"":criteria.model().strip().toLowerCase(java.util.Locale.ROOT);
            // Canonical Latin identifiers, e.g. /cars/bmw/m3/all/. Other names use site search.
            if(brand.matches("[a-z0-9]+") && (model.isEmpty() || model.matches("[a-z0-9]+")))
                return URI.create("https://auto.ru/cars/"+brand+"/"+(model.isEmpty()?"":model+"/")+"all/");
            String query=((criteria.brand()==null?"":criteria.brand())+" "+(criteria.model()==null?"":criteria.model())).strip();
            if(!query.isEmpty()) return URI.create("https://auto.ru/cars/all/?query="+
                java.net.URLEncoder.encode(query,java.nio.charset.StandardCharsets.UTF_8)+"&from=searchline");
        }
        if (value == null) value = type == SourceType.AVITO
            ? "https://www.avito.ru/rossiya/avtomobili" : "https://auto.ru/cars/used/";
        return URI.create(value);
    }
    public static String validateUrl(String value, SourceType type) {
        if (value == null || value.isBlank()) return null;
        URI uri;
        try { uri = URI.create(value.strip()); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Некорректная ссылка поиска"); }
        String host = uri.getHost();
        boolean accepted = type == SourceType.AVITO
            ? "www.avito.ru".equalsIgnoreCase(host) || "avito.ru".equalsIgnoreCase(host)
            : "auto.ru".equalsIgnoreCase(host) || "www.auto.ru".equalsIgnoreCase(host);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !accepted || uri.getPort() != -1
                || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Нужна HTTPS-ссылка поиска выбранной площадки");
        return uri.toString();
    }
}

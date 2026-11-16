package dev.autowatcher.settings;

import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
    private final JdbcTemplate jdbc;
    public SettingsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<String> get(String key) {
        return jdbc.query("SELECT value FROM app_settings WHERE key=?",
            (rs, n) -> rs.getString(1), key).stream().findFirst();
    }
    public String get(String key, String fallback) { return get(key).orElse(fallback); }
    public void set(String key, String value) {
        if (value == null) jdbc.update("DELETE FROM app_settings WHERE key=?", key);
        else jdbc.update("""
            INSERT INTO app_settings(key,value) VALUES(?,?)
            ON CONFLICT(key) DO UPDATE SET value=excluded.value
            """, key, value);
    }
    public Map<String,Object> publicSettings() {
        return Map.of("telegramConfigured", get("telegram_token").isPresent(),
            "telegramConnected", get("telegram_owner").isPresent(),
            "telegramStatus", get("telegram_status", "Не настроен"),
            "retentionDays", Integer.parseInt(get("retention_days", "30")));
    }
    public void setRetention(int days) {
        if (days < 1 || days > 3650) throw new IllegalArgumentException("Retention must be 1..3650 days");
        set("retention_days", Integer.toString(days));
    }
    @Transactional
    public void disconnectTelegram() {
        jdbc.update("DELETE FROM telegram_replies");
        jdbc.update("DELETE FROM telegram_updates");
        for (String key : new String[]{"telegram_token","telegram_owner","telegram_pair_code",
                "telegram_pair_expires","telegram_offset"}) set(key, null);
        set("telegram_status", "Отключён");
    }
}

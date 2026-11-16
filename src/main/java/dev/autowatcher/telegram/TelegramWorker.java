package dev.autowatcher.telegram;

import dev.autowatcher.settings.SettingsService;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.background.enabled",havingValue="true",matchIfMissing=true)
public class TelegramWorker {
    private final TelegramApi api;
    private final SettingsService settings;
    private final TelegramCommandService commands;
    private final JdbcTemplate jdbc;
    private volatile Instant nextPoll=Instant.EPOCH;
    public TelegramWorker(TelegramApi api,SettingsService settings,TelegramCommandService commands,JdbcTemplate jdbc) {
        this.api=api;this.settings=settings;this.commands=commands;this.jdbc=jdbc;
    }
    @Scheduled(fixedDelay=1000,initialDelay=5000)
    public void poll() {
        if(settings.get("telegram_token").isEmpty() || nextPoll.isAfter(Instant.now())) return;
        try {
            var updates=api.call("getUpdates",Map.of("offset",Long.parseLong(settings.get("telegram_offset","0")),
                "timeout",15,"limit",20,"allowed_updates",java.util.List.of("message")));
            for(var update:updates) {
                try { commands.accept(update); }
                catch(RuntimeException e) { commands.reject(update); }
            }
            settings.set("telegram_status",settings.get("telegram_owner").isPresent()?"Подключён":"Ожидается привязка владельца");
        } catch(TelegramApi.Failure e) { nextPoll=Instant.now().plusSeconds(e.retrySeconds()); settings.set("telegram_status",e.getMessage()); }
        catch(RuntimeException e) { settings.set("telegram_status","Ошибка обработки команд"); }
    }
    @Scheduled(fixedDelay=3000,initialDelay=5000)
    public void replies() {
        if(settings.get("telegram_token").isEmpty()) return;
        var rows=jdbc.queryForList("""
            SELECT * FROM telegram_replies WHERE status='PENDING' AND next_attempt_at<=?
            ORDER BY update_id LIMIT 10
            """,Instant.now().toString());
        for(var row:rows) {
            long id=((Number)row.get("update_id")).longValue();
            int attempts=((Number)row.get("attempts")).intValue()+1;
            try {
                api.send(row.get("chat_id").toString(),row.get("text").toString());
                jdbc.update("UPDATE telegram_replies SET status='DELIVERED',attempts=?,error_code=NULL WHERE update_id=?",attempts,id);
            } catch(TelegramApi.Failure e) {
                jdbc.update("""
                    UPDATE telegram_replies SET status=?,attempts=?,error_code=?,next_attempt_at=? WHERE update_id=?
                    """,attempts>=10?"DEAD":"PENDING",attempts,e.getMessage(),
                    Instant.now().plusSeconds(Math.max(e.retrySeconds(),Math.min(3600,30L<<Math.min(attempts,7)))).toString(),id);
            }
        }
    }
}

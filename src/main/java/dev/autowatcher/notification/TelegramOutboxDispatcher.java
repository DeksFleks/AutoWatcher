package dev.autowatcher.notification;

import dev.autowatcher.settings.SettingsService;
import dev.autowatcher.telegram.TelegramApi;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TelegramOutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final TelegramApi api;
    public TelegramOutboxDispatcher(JdbcTemplate jdbc,SettingsService settings,TelegramApi api) {
        this.jdbc=jdbc;this.settings=settings;this.api=api;
    }
    /** At-least-once delivery. A crash after Telegram accepts but before ACK can repeat a message. */
    public synchronized int dispatch() {
        String owner=settings.get("telegram_owner",null);
        if(owner==null||settings.get("telegram_token").isEmpty()) return 0;
        var events=jdbc.queryForList("""
            SELECT o.*,l.title,l.url,l.price,p.name AS profile_name FROM notification_outbox o
            JOIN listings l ON l.id=o.listing_id JOIN search_profiles p ON p.id=o.profile_id
            WHERE o.channel='TELEGRAM' AND o.status='PENDING' AND o.next_attempt_at<=?
            ORDER BY o.next_attempt_at LIMIT 20
            """,Instant.now().toString());
        int delivered=0;
        for(var event:events) {
            String id=event.get("id").toString();
            int attempts=((Number)event.get("attempts")).intValue()+1;
            try {
                api.send(owner,"Новое объявление: "+event.get("profile_name")+"\n"+event.get("title")+
                    "\nЦена: "+(event.get("price")==null?"не указана":event.get("price")+" ₽")+"\n"+event.get("url")+"\nСобытие: "+id);
                jdbc.update("""
                    UPDATE notification_outbox SET status='DELIVERED',delivered_at=?,attempts=?,error_code=NULL
                    WHERE id=? AND status='PENDING'
                    """,Instant.now().toString(),attempts,id);
                delivered++;
            } catch(TelegramApi.Failure e) {
                jdbc.update("""
                    UPDATE notification_outbox SET status=?,attempts=?,error_code=?,next_attempt_at=? WHERE id=?
                    """,attempts>=10?"DEAD":"PENDING",attempts,e.getMessage(),
                    Instant.now().plusSeconds(Math.max(e.retrySeconds(),Math.min(3600,30L<<Math.min(attempts,7)))).toString(),id);
            }
        }
        return delivered;
    }
    public void retry(UUID profileId) {
        jdbc.update("""
            UPDATE notification_outbox SET status='PENDING',attempts=0,next_attempt_at=?,error_code=NULL
            WHERE profile_id=? AND channel='TELEGRAM' AND status='DEAD'
            """,Instant.now().toString(),profileId.toString());
    }
}

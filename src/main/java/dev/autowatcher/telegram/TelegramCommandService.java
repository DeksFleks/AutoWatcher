package dev.autowatcher.telegram;

import com.fasterxml.jackson.databind.*;
import dev.autowatcher.monitoring.application.*;
import dev.autowatcher.search.application.*;
import dev.autowatcher.settings.SettingsService;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Telegram and HTTP use the same profile, monitoring and query services. */
@Service
public class TelegramCommandService {
    private final SearchProfileService profiles;
    private final ProfileMonitoringService monitoring;
    private final MonitoringQueryService queries;
    private final SettingsService settings;
    private final TelegramConnectionService connection;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public TelegramCommandService(SearchProfileService profiles,ProfileMonitoringService monitoring,
            MonitoringQueryService queries,SettingsService settings,TelegramConnectionService connection,
            JdbcTemplate jdbc,ObjectMapper json) {
        this.profiles=profiles;this.monitoring=monitoring;this.queries=queries;this.settings=settings;
        this.connection=connection;this.jdbc=jdbc;this.json=json;
    }
    @Transactional
    public void accept(JsonNode update) {
        long updateId=update.path("update_id").asLong(-1);
        if(updateId<0) return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM telegram_updates WHERE update_id=?",Integer.class,updateId)>0) return;
        JsonNode message=update.path("message");
        String chat=message.path("chat").path("id").asText("");
        String user=message.path("from").path("id").asText("");
        String text=message.path("text").asText("").strip();
        String reply=null;
        if(!chat.isEmpty() && "private".equals(message.path("chat").path("type").asText())) {
            if(text.startsWith("/start ") && connection.bind(text.substring(7).strip(),chat,user))
                reply="AutoWatcher подключён. /help — команды; /profiles — твои фильтры.";
            else if(chat.equals(settings.get("telegram_owner","")) && chat.equals(user)) {
                try { reply=execute(text); }
                catch(Exception e) { throw new IllegalArgumentException("Invalid Telegram command"); }
            }
        }
        jdbc.update("INSERT INTO telegram_updates(update_id) VALUES(?)",updateId);
        settings.set("telegram_offset",Long.toString(updateId+1));
        if(reply!=null) jdbc.update("""
            INSERT INTO telegram_replies(update_id,chat_id,text,next_attempt_at) VALUES(?,?,?,?)
            """,updateId,chat,reply,Instant.now().toString());
    }
    @Transactional
    public void reject(JsonNode update) {
        long id=update.path("update_id").asLong(-1);
        if(id<0) return;
        jdbc.update("INSERT INTO telegram_updates(update_id) VALUES(?) ON CONFLICT(update_id) DO NOTHING",id);
        settings.set("telegram_offset",Long.toString(id+1));
        String owner=settings.get("telegram_owner","");
        if(!owner.isEmpty() && owner.equals(update.path("message").path("chat").path("id").asText())
                && owner.equals(update.path("message").path("from").path("id").asText()))
            jdbc.update("""
                INSERT INTO telegram_replies(update_id,chat_id,text,next_attempt_at) VALUES(?,?,?,?)
                ON CONFLICT(update_id) DO NOTHING
                """,id,owner,"Команда не выполнена. Проверь ID и JSON; /help — примеры.",Instant.now().toString());
    }
    private String execute(String text) throws Exception {
        String[] parts=text.split("\\s+",3);
        String command=parts[0].split("@",2)[0];
        return switch(command) {
            case "/start","/help" -> help();
            case "/profiles" -> profiles.findAll().stream()
                .map(p->p.id()+" | "+p.name()+" | "+(p.enabled()?"включён":"выключен"))
                .collect(java.util.stream.Collectors.joining("\n","Фильтры:\n",""));
            case "/create" -> {
                String body=text.substring(parts[0].length()).strip();
                var created=profiles.create(json.readValue(body,CreateSearchProfileCommand.class));
                yield "Создан: "+created.id()+"\n"+created.name();
            }
            case "/edit" -> {
                var p=profiles.update(id(parts),json.readValue(parts[2],CreateSearchProfileCommand.class));
                yield "Сохранён: "+p.id()+"\n"+p.name();
            }
            case "/delete" -> { profiles.delete(id(parts)); yield "Фильтр удалён."; }
            case "/enable" -> {
                if(parts.length<3 || !Set.of("true","false").contains(parts[2]))
                    throw new IllegalArgumentException("Use true/false");
                profiles.setEnabled(id(parts),Boolean.parseBoolean(parts[2])); yield "Статус сохранён.";
            }
            case "/check" -> { monitoring.requestCheck(id(parts)); yield "Проверка поставлена в очередь. /results "+parts[1]; }
            case "/monitor" -> {
                monitoring.save(id(parts),json.readValue(parts[2],ProfileMonitoringService.Configuration.class));
                yield "Расписание и ссылки сохранены.";
            }
            case "/results" -> {
                var rows=queries.results(id(parts));
                yield rows.isEmpty()?"Результатов пока нет.":rows.stream().limit(10)
                    .map(r->r.get("result")+" | "+r.get("title")+" | "+r.get("price")+" ₽\n"+r.get("url"))
                    .collect(java.util.stream.Collectors.joining("\n\n"));
            }
            case "/runs" -> queries.runs(id(parts)).stream().limit(5)
                .map(r->r.get("source")+": "+r.get("status")+" "+Objects.toString(r.get("error_message"),""))
                .collect(java.util.stream.Collectors.joining("\n"));
            default -> "Неизвестная команда. /help";
        };
    }
    private UUID id(String[] parts) { return UUID.fromString(parts[1]); }
    public String help() {
        return """
            AutoWatcher
            /profiles — фильтры с ID
            /create {"name":"BMW","criteria":{"brand":"BMW","priceTo":3000000},"sources":["AVITO"]}
            /edit ID {"name":"BMW","criteria":{"brand":"BMW","priceTo":3500000},"sources":["AVITO"]}
            /monitor ID {"intervalSeconds":300,"scheduled":true,"avitoUrl":"https://www.avito.ru/rossiya/avtomobili"}
            /enable ID true — включить; false — выключить
            /check ID — запустить проверку
            /results ID — последние результаты
            /runs ID — история проверок
            /delete ID — удалить фильтр
            Все изменения сразу видны и в веб-панели.
            """;
    }
}

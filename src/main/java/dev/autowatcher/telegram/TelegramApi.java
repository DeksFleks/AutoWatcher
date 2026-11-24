package dev.autowatcher.telegram;

import com.fasterxml.jackson.databind.*;
import dev.autowatcher.settings.SettingsService;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Bot API exceptions deliberately exclude request URLs, which contain the token. */
@Component
public class TelegramApi {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TelegramApi.class);
    private final SettingsService settings;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public TelegramApi(SettingsService settings,ObjectMapper json) { this.settings=settings; this.json=json; }

    public JsonNode call(String method,Map<String,Object> payload) {
        String token=settings.get("telegram_token").orElseThrow(()->new Failure("NOT_CONFIGURED",60));
        if(!token.matches("[0-9]+:[A-Za-z0-9_-]{20,}")) throw new Failure("INVALID_TOKEN",60);
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot"+token+"/"+method))
                .timeout(Duration.ofSeconds(30)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
            if (!method.equals("getUpdates")) log.info("Telegram исходящий метод={} символов={}",method,Objects.toString(payload.get("text"),"").length());
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            JsonNode body=json.readTree(response.body());
            if(response.statusCode()!=200 || !body.path("ok").asBoolean())
                throw new Failure("TELEGRAM_"+body.path("error_code").asInt(response.statusCode()),
                    Math.max(30,Math.min(3600,body.path("parameters").path("retry_after").asInt(30))));
            var result=body.path("result");
            if (!method.equals("getUpdates") || !result.isEmpty()) log.info("Telegram метод={} HTTP={} message_id={} обновлений={}",method,response.statusCode(),result.path("message_id").asText("-"),result.isArray()?result.size():0);
            return result;
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new Failure("INTERRUPTED",30); }
        catch(Failure e) { log.warn("Telegram метод={} ошибка={} пауза={} с",method,e.getMessage(),e.retrySeconds()); throw e; }
        catch(Exception e) { log.warn("Telegram метод={} сетевая ошибка={}",method,e.getClass().getSimpleName()); throw new Failure("TELEGRAM_NETWORK_ERROR",30); }
    }
    public void send(String chatId,String message) {
        call("sendMessage",Map.of("chat_id",chatId,"text",message.length()>4000?message.substring(0,4000):message,
            "link_preview_options",Map.of("is_disabled",true)));
    }
    public static class Failure extends RuntimeException {
        private final int retrySeconds;
        public Failure(String code,int retrySeconds) { super(code); this.retrySeconds=retrySeconds; }
        public int retrySeconds() { return retrySeconds; }
    }
}

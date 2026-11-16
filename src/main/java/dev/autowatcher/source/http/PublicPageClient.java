package dev.autowatcher.source.http;

import dev.autowatcher.source.api.SourceException;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PublicPageClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, Instant> nextAllowed = new HashMap<>();
    private final Map<String, CachedRobots> robots = new HashMap<>();
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    public synchronized String get(URI uri) {
        validateHost(uri);
        Instant next = nextAllowed.getOrDefault(uri.getHost(),Instant.EPOCH);
        if (next.isAfter(Instant.now())) {
            try { Thread.sleep(Math.min(10000,Duration.between(Instant.now(),next).toMillis()+1)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SourceException("Проверка остановлена"); }
        }
        nextAllowed.put(uri.getHost(),Instant.now().plusSeconds(5));
        CachedRobots policy=robots.get(uri.getHost());
        if(policy==null || policy.expires().isBefore(Instant.now())) {
            String text = read(URI.create("https://" + uri.getHost() + "/robots.txt"),true);
            policy=new CachedRobots(text,Instant.now().plusSeconds(3600));
            robots.put(uri.getHost(),policy);
        }
        if(!RobotsPolicy.allows(policy.text(),uri))
            throw new SourceException("ROBOTS_DENIED: площадка запрещает автоматический доступ к этой странице");
        return read(uri,false);
    }
    private String read(URI uri, boolean robotsRequest) {
        try {
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent","AutoWatcher/0.2 (+local personal listing monitor)")
                .header("Accept", robotsRequest ? "text/plain" : "text/html,application/xhtml+xml").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream body=response.body()) {
                int status=response.statusCode();
                if(robotsRequest && status==404) return "";
                if(status==403 || status==429 || status==401)
                    throw new SourceException("ACCESS_BLOCKED: HTTP " + status + "; проверка доступа/лимита площадки");
                if(status!=200) throw new SourceException("SOURCE_HTTP_" + status + ": страница недоступна");
                byte[] bytes=body.readNBytes(MAX_BYTES+1);
                if(bytes.length>MAX_BYTES) throw new SourceException("SOURCE_RESPONSE_TOO_LARGE");
                String text=new String(bytes,StandardCharsets.UTF_8);
                String lower=text.toLowerCase(Locale.ROOT);
                if(!robotsRequest && (lower.contains("smartcaptcha") || lower.contains("captcha-container")
                        || lower.contains("доступ временно ограничен") || lower.contains("подтвердите, что вы не робот")))
                    throw new SourceException("CAPTCHA_REQUIRED: площадка требует ручную проверку доступа");
                return text;
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); throw new SourceException("Проверка остановлена");
        } catch(IOException e) {
            throw new SourceException("NETWORK_ERROR: не удалось загрузить страницу; проверь соединение");
        }
    }
    public static void validateHost(URI uri) {
        if(uri==null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null
                || uri.getPort()!=-1 || !Set.of("avito.ru","www.avito.ru","auto.ru","www.auto.ru").contains(uri.getHost()))
            throw new SourceException("INVALID_SOURCE_URL: нужна ссылка выбранной площадки");
    }
    private record CachedRobots(String text, Instant expires) {}
}

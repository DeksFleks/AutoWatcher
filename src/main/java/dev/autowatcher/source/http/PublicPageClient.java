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
    private final HttpClient client;
    public PublicPageClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    PublicPageClient(HttpClient client) { this.client=client; }
    private final Map<String, Instant> nextAllowed = new HashMap<>();
    private final Map<String, CachedRobots> robots = new HashMap<>();
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    public synchronized String get(URI uri) {
        validateHost(uri);
        Instant next = nextAllowed.getOrDefault(uri.getHost(),Instant.EPOCH);
        if (next.isAfter(Instant.now())) {
            if(Duration.between(Instant.now(),next).toSeconds()>10)
                throw new SourceException("SOURCE_COOLDOWN: следующая попытка после " + next);
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
    private String read(URI uri, boolean robotsRequest) { return read(uri,robotsRequest,0); }
    private String read(URI uri, boolean robotsRequest,int redirects) {
        try {
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent","AutoWatcher/0.2 (+local personal listing monitor)")
                .header("Accept", robotsRequest ? "text/plain" : "text/html,application/xhtml+xml").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream body=response.body()) {
                int status=response.statusCode();
                if(robotsRequest && status==404) return "";
                if(status==301 || status==302 || status==307 || status==308) {
                    URI next=uri.resolve(response.headers().firstValue("Location").orElse(""));
                    validateHost(next);
                    if(next.getPath().toLowerCase(Locale.ROOT).contains("captcha"))
                        throw new SourceException("CAPTCHA_REQUIRED: площадка перенаправляет на проверку доступа");
                    if(robotsRequest || !next.getHost().equals(uri.getHost()) || redirects>=3)
                        throw new SourceException("SOURCE_REDIRECT: " + next.getHost() + next.getPath());
                    CachedRobots policy=robots.get(uri.getHost());
                    if(policy==null || !RobotsPolicy.allows(policy.text(),next))
                        throw new SourceException("ROBOTS_DENIED: автоматический доступ к перенаправлению запрещён");
                    Thread.sleep(5000);
                    nextAllowed.put(uri.getHost(),Instant.now().plusSeconds(5));
                    return read(next,false,redirects+1);
                }
                if(status==403 || status==429 || status==401) {
                    long pause=300;
                    try { pause=Long.parseLong(response.headers().firstValue("Retry-After").orElse("300")); }
                    catch(NumberFormatException ignored) { }
                    nextAllowed.put(uri.getHost(),Instant.now().plusSeconds(Math.max(60,Math.min(3600,pause))));
                    throw new SourceException("ACCESS_BLOCKED: HTTP " + status + "; проверка доступа/лимита площадки");
                }
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

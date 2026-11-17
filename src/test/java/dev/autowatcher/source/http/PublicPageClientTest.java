package dev.autowatcher.source.http;

import dev.autowatcher.source.api.SourceException;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicPageClientTest {
    @SuppressWarnings("unchecked")
    @Test void rateLimitIsReportedAndSubsequentChecksWaitWithoutAnotherRequest() throws Exception {
        HttpClient http=mock(HttpClient.class);
        HttpResponse<InputStream> robots=mock(HttpResponse.class);
        when(robots.statusCode()).thenReturn(200);
        when(robots.body()).thenReturn(new ByteArrayInputStream("User-agent: *\nAllow: /".getBytes(StandardCharsets.UTF_8)));
        HttpResponse<InputStream> limited=mock(HttpResponse.class);
        when(limited.statusCode()).thenReturn(429);
        when(limited.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(limited.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("120")),(k,v)->true));
        when(http.send(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any())).thenReturn(robots,limited);
        var client=new PublicPageClient(http);
        URI uri=URI.create("https://www.avito.ru/rossiya/avtomobili");
        assertTrue(assertThrows(SourceException.class,()->client.get(uri)).getMessage().contains("HTTP 429"));
        assertTrue(assertThrows(SourceException.class,()->client.get(uri)).getMessage().contains("SOURCE_COOLDOWN"));
        verify(http,times(2)).send(any(HttpRequest.class),ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());
    }
    @Test void nonPlatformAndCredentialUrlsAreRejected() {
        assertThrows(SourceException.class,()->PublicPageClient.validateHost(URI.create("http://auto.ru/cars")));
        assertThrows(SourceException.class,()->PublicPageClient.validateHost(URI.create("https://auto.ru.evil.test/")));
        assertThrows(SourceException.class,()->PublicPageClient.validateHost(URI.create("https://user@auto.ru/")));
    }
}

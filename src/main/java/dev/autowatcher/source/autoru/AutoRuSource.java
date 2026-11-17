package dev.autowatcher.source.autoru;

import dev.autowatcher.search.domain.SourceType;
import dev.autowatcher.source.api.*;
import dev.autowatcher.source.http.*;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AutoRuSource implements ListingSource {
    private final PublicPageClient client;
    private final ListingPageParser parser;
    public AutoRuSource(PublicPageClient client, ListingPageParser parser) { this.client=client; this.parser=parser; }
    @Override public SourceType type() { return SourceType.AUTO_RU; }
    @Override public SourceCapabilities capabilities() { return new SourceCapabilities(Set.of()); }
    @Override public SearchPage search(SourceQuery query) {
        URI url=query.cursor()==null?query.searchUrl():URI.create(query.cursor());
        PublicPageClient.validateHost(url);
        if(!url.getHost().endsWith("auto.ru"))
            throw new SourceException("INVALID_SOURCE_URL: неверная площадка");
        return parser.parse(client.get(url),url,type());
    }
}

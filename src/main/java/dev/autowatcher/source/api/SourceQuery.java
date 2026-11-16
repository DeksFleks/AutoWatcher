package dev.autowatcher.source.api;
import dev.autowatcher.search.domain.SearchCriteria;
import java.net.URI;
public record SourceQuery(SearchCriteria criteria, int limit, String cursor, URI searchUrl) {
    public SourceQuery(SearchCriteria criteria, int limit, String cursor) {
        this(criteria,limit,cursor,null);
    }
}

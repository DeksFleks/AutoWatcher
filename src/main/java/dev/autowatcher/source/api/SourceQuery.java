package dev.autowatcher.source.api;
import dev.autowatcher.search.domain.SearchCriteria;
public record SourceQuery(SearchCriteria criteria, int limit, String cursor) {}

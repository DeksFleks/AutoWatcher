package dev.autowatcher.source.api;
import dev.autowatcher.search.domain.SourceType;
public interface ListingSource {
    SourceType type();
    SourceCapabilities capabilities();
    SearchPage search(SourceQuery query) throws SourceException;
}

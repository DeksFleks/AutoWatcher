package dev.autowatcher.source.avito;

import dev.autowatcher.search.domain.SourceType;
import dev.autowatcher.source.api.*;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AvitoSource implements ListingSource {
    @Override public SourceType type() { return SourceType.AVITO; }
    @Override public SourceCapabilities capabilities() { return new SourceCapabilities(Set.of()); }
    @Override public SearchPage search(SourceQuery query) {
        throw new SourceException("AVITO adapter is not implemented yet");
    }
}

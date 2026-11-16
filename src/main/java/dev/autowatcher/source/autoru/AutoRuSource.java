package dev.autowatcher.source.autoru;

import dev.autowatcher.search.domain.SourceType;
import dev.autowatcher.source.api.*;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AutoRuSource implements ListingSource {
    @Override public SourceType type() { return SourceType.AUTO_RU; }
    @Override public SourceCapabilities capabilities() { return new SourceCapabilities(Set.of()); }
    @Override public SearchPage search(SourceQuery query) {
        throw new SourceException("AUTO_RU adapter is not implemented yet");
    }
}

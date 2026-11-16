package dev.autowatcher.search.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SearchProfile(UUID id, String name, boolean enabled,
                            SearchCriteria criteria, Set<SourceType> sources,
                            Instant createdAt, Instant updatedAt) {
    public SearchProfile {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        if (criteria == null) throw new IllegalArgumentException("Criteria are required");
        if (sources == null || sources.isEmpty()) throw new IllegalArgumentException("Select at least one source");
        sources = Set.copyOf(sources);
    }
}

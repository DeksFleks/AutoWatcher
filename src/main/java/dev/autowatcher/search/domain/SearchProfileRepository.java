package dev.autowatcher.search.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchProfileRepository {
    void save(SearchProfile profile);
    List<SearchProfile> findAll();
    Optional<SearchProfile> findById(UUID id);
    boolean delete(UUID id);
}

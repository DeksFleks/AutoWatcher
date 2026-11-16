package dev.autowatcher.search.application;

import dev.autowatcher.search.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchProfileService {
    private final SearchProfileRepository repository;
    public SearchProfileService(SearchProfileRepository repository) { this.repository = repository; }

    @Transactional
    public SearchProfile create(CreateSearchProfileCommand command) {
        Instant now = Instant.now();
        SearchProfile profile = new SearchProfile(UUID.randomUUID(), command.name(), true,
            command.criteria(), command.sources(), now, now);
        repository.save(profile);
        return profile;
    }
    public List<SearchProfile> findAll() { return repository.findAll(); }
    public SearchProfile findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProfileNotFoundException(id));
    }
    @Transactional
    public SearchProfile update(UUID id, CreateSearchProfileCommand command) {
        SearchProfile old = findById(id);
        SearchProfile updated = new SearchProfile(id, command.name(), old.enabled(),
            command.criteria(), command.sources(), old.createdAt(), Instant.now());
        repository.save(updated);
        return updated;
    }
    @Transactional
    public SearchProfile setEnabled(UUID id, boolean enabled) {
        SearchProfile old = findById(id);
        SearchProfile updated = new SearchProfile(id, old.name(), enabled,
            old.criteria(), old.sources(), old.createdAt(), Instant.now());
        repository.save(updated);
        return updated;
    }
    @Transactional
    public void delete(UUID id) {
        findById(id);
        if (!repository.delete(id)) throw new ProfileNotFoundException(id);
    }
}

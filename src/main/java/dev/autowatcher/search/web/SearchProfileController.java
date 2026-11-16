package dev.autowatcher.search.web;

import dev.autowatcher.search.application.*;
import dev.autowatcher.search.domain.SearchProfile;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profiles")
public class SearchProfileController {
    private final SearchProfileService service;
    public SearchProfileController(SearchProfileService service) { this.service = service; }

    @GetMapping
    public List<SearchProfile> all() { return service.findAll(); }
    @GetMapping("/{id}")
    public SearchProfile one(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping
    public ResponseEntity<SearchProfile> create(@Valid @RequestBody CreateSearchProfileCommand command) {
        SearchProfile profile = service.create(command);
        return ResponseEntity.created(URI.create("/api/v1/profiles/" + profile.id())).body(profile);
    }
    @PutMapping("/{id}")
    public SearchProfile update(@PathVariable UUID id, @Valid @RequestBody CreateSearchProfileCommand command) {
        return service.update(id, command);
    }
    @PatchMapping("/{id}/status")
    public SearchProfile status(@PathVariable UUID id, @RequestBody StatusRequest request) {
        return service.setEnabled(id, request.enabled());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
    public record StatusRequest(boolean enabled) {}
}

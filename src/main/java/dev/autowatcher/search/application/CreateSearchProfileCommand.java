package dev.autowatcher.search.application;

import dev.autowatcher.search.domain.SearchCriteria;
import dev.autowatcher.search.domain.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record CreateSearchProfileCommand(
    @NotBlank String name,
    @Valid SearchCriteria criteria,
    @NotEmpty Set<SourceType> sources
) {}

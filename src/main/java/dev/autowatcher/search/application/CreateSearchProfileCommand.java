package dev.autowatcher.search.application;

import dev.autowatcher.search.domain.SearchCriteria;
import dev.autowatcher.search.domain.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Set;

public record CreateSearchProfileCommand(
    @NotBlank String name,
    @NotNull @Valid SearchCriteria criteria,
    @NotEmpty Set<@NotNull SourceType> sources
) {}

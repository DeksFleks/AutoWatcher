package dev.autowatcher.monitoring.domain;

import dev.autowatcher.search.domain.SourceType;
import java.time.Instant;
import java.util.UUID;

public record MonitoringRun(UUID id, UUID profileId, SourceType source, String status,
    Instant startedAt, Instant finishedAt, String errorMessage) {}

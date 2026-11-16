package dev.autowatcher.listing.domain;

import dev.autowatcher.search.domain.SourceType;
import java.net.URI;
import java.time.Instant;

public record CarListing(SourceType source, String externalId, URI url,
    String title, String brand, String model, Integer year, Long price,
    Integer mileage, String region, Instant observedAt) {}

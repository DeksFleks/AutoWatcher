package dev.autowatcher.source.api;
import dev.autowatcher.listing.domain.CarListing;
import java.util.List;
public record SearchPage(List<CarListing> listings, String nextCursor) {}

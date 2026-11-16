package dev.autowatcher.listing.persistence;

import dev.autowatcher.listing.domain.CarListing;
import java.util.UUID;

public interface ListingRepository {
    UUID upsert(CarListing listing);
}

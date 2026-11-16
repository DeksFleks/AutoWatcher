package dev.autowatcher.listing.application;

import dev.autowatcher.listing.domain.*;
import dev.autowatcher.search.domain.SearchCriteria;
import org.springframework.stereotype.Service;

@Service
public class MatchingService {
    public MatchResult match(SearchCriteria c, CarListing l) {
        boolean unknown = false;
        if (c.brand() != null && !c.brand().isBlank()) {
            if (l.brand() == null || l.brand().isBlank()) unknown = true;
            else if (!c.brand().equalsIgnoreCase(l.brand())) return MatchResult.NO_MATCH;
        }
        if (c.model() != null && !c.model().isBlank()) {
            if (l.model() == null || l.model().isBlank()) unknown = true;
            else if (!c.model().equalsIgnoreCase(l.model())) return MatchResult.NO_MATCH;
        }
        if (c.yearFrom() != null || c.yearTo() != null) {
            if (l.year() == null) unknown = true;
            else if (c.yearFrom() != null && l.year() < c.yearFrom() ||
                     c.yearTo() != null && l.year() > c.yearTo()) return MatchResult.NO_MATCH;
        }
        if (c.priceFrom() != null || c.priceTo() != null) {
            if (l.price() == null) unknown = true;
            else if (c.priceFrom() != null && l.price() < c.priceFrom() ||
                     c.priceTo() != null && l.price() > c.priceTo()) return MatchResult.NO_MATCH;
        }
        if (c.mileageTo() != null) {
            if (l.mileage() == null) unknown = true;
            else if (l.mileage() > c.mileageTo()) return MatchResult.NO_MATCH;
        }
        if (c.region() != null && !c.region().isBlank()) {
            if (l.region() == null || l.region().isBlank()) unknown = true;
            else if (!l.region().equalsIgnoreCase(c.region())) return MatchResult.NO_MATCH;
        }
        return unknown ? MatchResult.UNKNOWN : MatchResult.MATCH;
    }
}

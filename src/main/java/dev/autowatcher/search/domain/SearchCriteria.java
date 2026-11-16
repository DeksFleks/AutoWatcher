package dev.autowatcher.search.domain;

public record SearchCriteria(
    String brand, String model,
    Integer yearFrom, Integer yearTo,
    Long priceFrom, Long priceTo,
    Integer mileageTo, String region
) {
    public SearchCriteria {
        if (yearFrom != null && yearTo != null && yearFrom > yearTo)
            throw new IllegalArgumentException("yearFrom must be <= yearTo");
        if (priceFrom != null && priceFrom < 0 || priceTo != null && priceTo < 0)
            throw new IllegalArgumentException("Prices must be nonnegative");
        if (priceFrom != null && priceTo != null && priceFrom > priceTo)
            throw new IllegalArgumentException("priceFrom must be <= priceTo");
        if (mileageTo != null && mileageTo < 0)
            throw new IllegalArgumentException("mileageTo must be nonnegative");
    }
}

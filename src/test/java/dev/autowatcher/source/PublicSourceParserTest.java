package dev.autowatcher.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.autowatcher.source.http.*;
import dev.autowatcher.source.api.SourceException;
import dev.autowatcher.search.domain.SourceType;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicSourceParserTest {
    private final ListingPageParser parser=new ListingPageParser(new ObjectMapper());
    @Test void parsesSemanticFixtureAndPreservesPaginationQuery() {
        String fixture="""
            <html><script type="application/ld+json">
            {"@type":"ItemList","itemListElement":[{"@type":"Car","name":"BMW 5 Series","brand":{"name":"BMW"},
            "model":"5 Series","vehicleModelDate":"2021","mileageFromOdometer":{"value":65000,"unitCode":"KMT"},
            "url":"https://www.avito.ru/moskva/avtomobili/bmw_123456","offers":{"price":"2500000.00","priceCurrency":"RUB"}}]}
            </script><a rel="next" href="?p=2">next</a></html>
            """;
        var page=parser.parse(fixture,URI.create("https://www.avito.ru/rossiya/avtomobili?p=1"),SourceType.AVITO);
        assertEquals(1,page.listings().size());
        assertEquals("BMW",page.listings().getFirst().brand());
        assertEquals(2500000L,page.listings().getFirst().price());
        assertEquals(65000,page.listings().getFirst().mileage());
        assertTrue(page.nextCursor().contains("p=2"));
    }
    @Test void unknownMarkupIsAnErrorAndExplicitEmptyResultIsEmpty() {
        assertThrows(SourceException.class,()->parser.parse("<html>Challenge</html>",URI.create("https://auto.ru/cars/used/"),SourceType.AUTO_RU));
        assertTrue(parser.parse("<html>Ничего не найдено</html>",URI.create("https://auto.ru/cars/used/"),SourceType.AUTO_RU).listings().isEmpty());
    }
    @Test void hostileListingLinksAreIgnored() {
        String html="<div data-marker='item'><a data-marker='item-title' href='https://evil.test/car'>BMW</a></div>";
        assertThrows(SourceException.class,()->parser.parse(html,URI.create("https://www.avito.ru/"),SourceType.AVITO));
    }
    @Test void parsesCurrentAutoRuCardWithHashedClasses() {
        String html="""
            <script type="application/ld+json">
            {"@type":"Product","name":"Продажа автомобилей FORD","offers":{"@type":"AggregateOffer","offers":[
            {"@type":"Offer","url":"https://auto.ru/cars/used/sale/ford/focus/1234567890-abcd/",
             "image":{"name":"Ford Focus III"},"price":695000,"priceCurrency":"RUR"}]}}
            </script>
            <div data-seo="listing-item"><a class="ListingItemTitle__link"
            href="/cars/used/sale/ford/focus/1234567890-abcd/">Ford Focus III, 2013, 216 151 км</a>
            <div class="ListingItemUniversalPrice__title-vqOQR">695 000 ₽</div>
            <span class="MetroListPlace__regionName">Москва</span></div>
            <a class="ListingPagination__next" href="?page=2">Следующая</a>
            """;
        var page=parser.parse(html,URI.create("https://auto.ru/cars/ford/all/"),SourceType.AUTO_RU);
        var car=page.listings().getFirst();
        assertEquals(1,page.listings().size());
        assertEquals("ford",car.brand()); assertEquals("focus",car.model());
        assertEquals(2013,car.year()); assertEquals(216151,car.mileage());
        assertEquals(695000L,car.price()); assertEquals("Москва",car.region());
        assertEquals("1234567890",car.externalId());
        assertEquals("https://auto.ru/cars/ford/all/?page=2",page.nextCursor());
        var criteria=new dev.autowatcher.search.domain.SearchCriteria("Ford","Focus",2013,2013,695000L,695000L,216151,"Москва");
        assertEquals(dev.autowatcher.listing.domain.MatchResult.MATCH,
            new dev.autowatcher.listing.application.MatchingService().match(criteria,car));
    }
    @Test void regionalWarningHasActionableError() {
        assertTrue(assertThrows(SourceException.class,()->parser.parse(
            "Сайт не предназначен для вашего региона",URI.create("https://auto.ru/cars/used/"),SourceType.AUTO_RU))
            .getMessage().startsWith("REGION_RESTRICTED"));
    }
    @Test void robotsLongestRuleAndGroups() {
        String robots="User-agent: *\nDisallow: /cars\nAllow: /cars/public\nUser-agent: Other\nDisallow: /";
        assertFalse(RobotsPolicy.allows(robots,URI.create("https://auto.ru/cars/used/")));
        assertTrue(RobotsPolicy.allows(robots,URI.create("https://auto.ru/cars/public/")));
        assertTrue(RobotsPolicy.allows(robots,URI.create("https://auto.ru/other")));
        assertFalse(RobotsPolicy.allows("User-agent: *\nDisallow: /*?p=",URI.create("https://auto.ru/cars?p=2")));
    }
}

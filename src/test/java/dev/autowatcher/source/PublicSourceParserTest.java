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
    @Test void parsesAvitoTitleInsideLinkAndVisiblePrice() {
        String html="""
            <div data-marker="item"><a href="/moskva/avtomobili/bmw_123456">
              <h3 data-marker="item-title">BMW X5, 2021</h3></a>
              <span data-marker="item-price">2 500 000 ₽</span></div>
            """;
        var listings=parser.parse(html,URI.create("https://www.avito.ru/"),SourceType.AVITO).listings();
        assertEquals(1,listings.size());
        assertEquals("BMW X5, 2021",listings.getFirst().title());
        assertEquals(2500000L,listings.getFirst().price());
    }
    @Test void hostileListingLinksAreIgnored() {
        String html="<div data-marker='item'><a data-marker='item-title' href='https://evil.test/car'>BMW</a></div>";
        assertThrows(SourceException.class,()->parser.parse(html,URI.create("https://www.avito.ru/"),SourceType.AVITO));
    }
    @Test void robotsLongestRuleAndGroups() {
        String robots="User-agent: *\nDisallow: /cars\nAllow: /cars/public\nUser-agent: Other\nDisallow: /";
        assertFalse(RobotsPolicy.allows(robots,URI.create("https://auto.ru/cars/used/")));
        assertTrue(RobotsPolicy.allows(robots,URI.create("https://auto.ru/cars/public/")));
        assertTrue(RobotsPolicy.allows(robots,URI.create("https://auto.ru/other")));
        assertFalse(RobotsPolicy.allows("User-agent: *\nDisallow: /*?p=",URI.create("https://auto.ru/cars?p=2")));
    }
}

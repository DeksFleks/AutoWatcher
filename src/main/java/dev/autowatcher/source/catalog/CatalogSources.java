package dev.autowatcher.source.catalog;

import dev.autowatcher.listing.domain.CarListing;
import dev.autowatcher.search.domain.SourceType;
import dev.autowatcher.source.api.*;
import dev.autowatcher.source.http.PublicPageClient;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.context.annotation.*;

@Configuration
public class CatalogSources {
    @Bean ListingSource dromSource(PublicPageClient client) {
        return new Catalog(client, SourceType.DROM, URI.create("https://auto.drom.ru/"),
            "[data-ftid=bulls-list_bull]", "a[href$=.html]", "h3", "[data-ftid=bull_price]");
    }
    @Bean ListingSource avtomirSource(PublicPageClient client) {
        return new Catalog(client, SourceType.AVTOMIR, URI.create("https://avtomir.ru/used-cars/"),
            ".card_big", "a.card__name", ".card__name", ".card__price-num");
    }
    @Bean ListingSource ttsSource(PublicPageClient client) {
        return new Catalog(client, SourceType.TTS, URI.create("https://www.tts.ru/sprobegom/"),
            "a.preview-card_auto[href*=detail.php]", "", ".descriprion-model", ".now-price-text > span:first-child");
    }
    @Bean ListingSource majorSource(PublicPageClient client) {
        return new Catalog(client, SourceType.MAJOR, URI.create("https://www.major-expert.ru/cars/"),
            "a.car-card[href]", "", ".car-card__title", "meta[itemprop=price]");
    }
    @Bean ListingSource borishofSource(PublicPageClient client) {
        return new Catalog(client, SourceType.BORISHOF, URI.create("https://borishof.ru/cars/used/"),
            "a.catalog__item[href*=/cars/used/auto-]", "", ".catalog-item__title", ".catalog-item__price_new");
    }
    @Bean ListingSource maximumSource(PublicPageClient client) {
        return new Catalog(client, SourceType.MAXIMUM, URI.create("https://maximum-auto.ru/in-stock/"),
            "a[class*=CarCard_root__][href]", "", "[class*=CarCard_title__]", "[class*=CarCard_priceValue__]");
    }

    record Catalog(PublicPageClient client, SourceType type, URI page, String cards,
                   String links, String titles, String prices) implements ListingSource {
        private static final Pattern YEAR = Pattern.compile("\\b(?:19[5-9]\\d|20\\d{2})\\b");
        private static final Pattern KM = Pattern.compile("(?<!\\d)(\\d{1,3}(?:[ \\u00a0]\\d{3})+|\\d+)\\s*км(?![а-яё])");
        public SourceCapabilities capabilities() { return new SourceCapabilities(Set.of()); }
        public SearchPage search(SourceQuery query) { return parse(client.get(page)); }

        SearchPage parse(String html) {
            var found = new LinkedHashMap<String, CarListing>();
            for (Element card : Jsoup.parse(html, page.toString()).select(cards)) {
                Element link = links.isEmpty() ? card : card.selectFirst(links);
                Element heading = card.selectFirst(titles);
                if (link == null || heading == null || heading.text().isBlank()) continue;
                URI url;
                try { url = page.resolve(link.attr("href").split("#",2)[0]); }
                catch (IllegalArgumentException e) { continue; }
                if (!page.getHost().equals(url.getHost()) || !"https".equals(url.getScheme())
                        || url.getUserInfo()!=null || url.getPort()!=-1) continue;
                String title = heading.ownText().isBlank() ? heading.text() : heading.ownText();
                String[] vehicle = title.split(",")[0].trim().split("\\s+",2);
                var year = YEAR.matcher(card.text());
                var mileage = KM.matcher(card.text());
                Element price = card.selectFirst(prices);
                String amount = price == null ? "" : (price.hasAttr("content") ? price.attr("content") : price.text())
                    .replaceAll("[\\s\\u00a0₽]", "");
                String id = url.getRawQuery()==null ? url.getPath() : url.getPath()+"?"+url.getRawQuery();
                found.putIfAbsent(id,new CarListing(type,id,url,title,vehicle[0],vehicle.length>1?vehicle[1]:null,
                    year.find()?Integer.valueOf(year.group()):null,amount.matches("[0-9]{1,12}")?Long.valueOf(amount):null,
                    mileage.find()?parseMileage(mileage.group(1)):null,
                    card.select("[data-ftid=bull_location]").stream().findFirst().map(Element::text).orElse(null),Instant.now()));
            }
            if (found.isEmpty()) throw new SourceException("MARKUP_CHANGED: карточки каталога не распознаны");
            return new SearchPage(List.copyOf(found.values()),null);
        }
        private Integer parseMileage(String value) {
            String digits = value.replaceAll("[^0-9]", "");
            return digits.length()<=9 ? Integer.valueOf(digits) : null;
        }
    }
}

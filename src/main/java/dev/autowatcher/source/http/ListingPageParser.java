package dev.autowatcher.source.http;

import com.fasterxml.jackson.databind.*;
import dev.autowatcher.listing.domain.CarListing;
import dev.autowatcher.search.domain.SourceType;
import dev.autowatcher.source.api.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.stereotype.Component;

/** Parses public HTML/JSON-LD only; never supplies invented values for absent attributes. */
@Component
public class ListingPageParser {
    private final ObjectMapper json;
    public ListingPageParser(ObjectMapper json) { this.json=json; }
    public SearchPage parse(String html, URI pageUrl, SourceType type) {
        Document doc=Jsoup.parse(html,pageUrl.toString());
        LinkedHashMap<String,CarListing> found=new LinkedHashMap<>();
        for(Element script:doc.select("script[type=application/ld+json]")) {
            try { visit(json.readTree(script.data()),pageUrl,type,found); }
            catch(java.io.IOException ignored) { /* Other metadata scripts may be malformed. */ }
        }
        String selector=type==SourceType.AVITO ? "[data-marker=item]" : ".ListingItem";
        for(Element card:doc.select(selector)) {
            Element link=card.selectFirst(type==SourceType.AVITO ? "a[data-marker=item-title]" : "a.ListingItemTitle__link");
            if(link==null) continue;
            URI url=safeResolve(pageUrl,link.attr("href"),type);
            if(url==null) continue;
            String title=link.text().strip();
            Element price=card.selectFirst(type==SourceType.AVITO ? "[itemprop=price]" : ".ListingItemPrice__content");
            Long value=price==null ? null : digits(price.hasAttr("content")?price.attr("content"):price.text());
            String details=card.text();
            Integer year=year(details);
            Integer mileage=null;
            Matcher km=Pattern.compile("([0-9][0-9\\s\\u00a0]*)\\s*км",Pattern.CASE_INSENSITIVE).matcher(details);
            if(km.find()) mileage=integer(digits(km.group(1)));
            String brand=null,model=null;
            // Titles are presentation text, not a reliable structured brand/model field.
            found.putIfAbsent(url.toString(),new CarListing(type,externalId(url),url,title,brand,model,
                year,value,mileage,null,Instant.now()));
        }
        if(found.isEmpty()) {
            String text=doc.text().toLowerCase(Locale.ROOT);
            if(!text.contains("ничего не найдено") && !text.contains("объявлений не найдено")
                    && !text.contains("нет объявлений"))
                throw new SourceException("MARKUP_CHANGED: объявления не распознаны; проверь доступность и разметку");
        }
        Element next=doc.selectFirst("a[rel=next], a[data-marker=pagination-button/next], a.ListingPagination__next");
        URI nextUrl=null;
        if(next!=null) {
            try {
                String href=next.attr("href");
                URI candidate=href.startsWith("?")?URI.create(pageUrl.toString().split("\\?",2)[0]+href):pageUrl.resolve(href);
                PublicPageClient.validateHost(candidate);
                if(candidate.getHost().equals(pageUrl.getHost())) nextUrl=candidate;
            } catch(RuntimeException ignored) { }
        }
        return new SearchPage(List.copyOf(found.values()),nextUrl==null?null:nextUrl.toString());
    }
    private void visit(JsonNode node, URI base, SourceType type, Map<String,CarListing> found) {
        if(node==null) return;
        if(node.isArray()) { node.forEach(n->visit(n,base,type,found)); return; }
        if(!node.isObject()) return;
        String kind=node.path("@type").asText();
        if(Set.of("Car","Vehicle","Product").contains(kind) && node.has("offers")) {
            URI url=safeResolve(base,node.path("url").asText(node.path("offers").path("url").asText("")),type);
            String title=node.path("name").asText("");
            if(url!=null && !title.isBlank()) {
                JsonNode offer=node.path("offers"); if(offer.isArray()) offer=offer.path(0);
                String currency=offer.path("priceCurrency").asText("RUB");
                Long price="RUB".equalsIgnoreCase(currency)?money(offer.path("price").asText()):null;
                String brand=scalar(node.path("brand"));
                String model=scalar(node.path("model"));
                Integer year=year(node.path("vehicleModelDate").asText(node.path("productionDate").asText("")));
                JsonNode mileage=node.path("mileageFromOdometer");
                Integer km=null;
                String unit=mileage.path("unitCode").asText("KMT");
                if(unit.equals("KMT")||unit.equals("km")) km=integer(digits(mileage.path("value").asText()));
                String region=node.path("offers").path("availableAtOrFrom").path("address")
                    .path("addressLocality").asText(null);
                found.put(url.toString(),new CarListing(type,externalId(url),url,title,brand,model,year,price,km,region,Instant.now()));
            }
        }
        node.elements().forEachRemaining(n->{ if(n.isContainerNode()) visit(n,base,type,found); });
    }
    private String scalar(JsonNode node) {
        String value=node.isTextual()?node.asText():node.path("name").asText(null);
        return value==null||value.isBlank()?null:value;
    }
    private URI safeResolve(URI base,String value,SourceType type) {
        if(value==null||value.isBlank()) return null;
        try {
            URI url=base.resolve(value);
            PublicPageClient.validateHost(url);
            boolean avito=url.getHost().endsWith("avito.ru");
            if((type==SourceType.AVITO)!=avito) return null;
            return new URI(url.getScheme(),null,url.getHost(),-1,url.getPath(),null,null);
        } catch(Exception e) { return null; }
    }
    private String externalId(URI uri) {
        Matcher matcher=Pattern.compile("(?:_|/)([0-9]{5,})(?:-[a-zA-Z0-9]+)?/?$").matcher(uri.getPath());
        return matcher.find()?matcher.group(1):uri.getPath();
    }
    private Long money(String text) {
        try {
            String value=text.replaceAll("[^0-9.,]","").replace(',','.');
            return value.isEmpty()?null:new java.math.BigDecimal(value).longValueExact();
        } catch(ArithmeticException|NumberFormatException e) { return null; }
    }
    private Long digits(String text) {
        if(text==null || text.isBlank()) return null;
        String value=text.replaceAll("[^0-9]","");
        if(value.isEmpty()) return null;
        try{return Long.valueOf(value);}catch(NumberFormatException e){return null;}
    }
    private Integer integer(Long number) {
        return number==null||number>Integer.MAX_VALUE?null:number.intValue();
    }
    private Integer year(String text) {
        Matcher matcher=Pattern.compile("\\b(19[5-9][0-9]|20[0-9]{2})\\b").matcher(text);
        return matcher.find()?Integer.valueOf(matcher.group(1)):null;
    }
}

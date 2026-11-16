package dev.autowatcher.telegram;

import com.fasterxml.jackson.databind.*;
import dev.autowatcher.monitoring.application.*;
import dev.autowatcher.notification.TelegramOutboxDispatcher;
import dev.autowatcher.search.application.*;
import dev.autowatcher.search.domain.*;
import dev.autowatcher.listing.domain.CarListing;
import dev.autowatcher.settings.SettingsService;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:sqlite::memory:")
class TelegramIntegrationTest {
    @Autowired TelegramConnectionService connection;
    @Autowired TelegramCommandService commands;
    @Autowired SettingsService settings;
    @Autowired SearchProfileService profiles;
    @Autowired ListingIngestionService ingestion;
    @Autowired ProfileMonitoringService monitoring;
    @Autowired TelegramOutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean TelegramApi api;

    @BeforeEach void reset() {
        for(String table:List.of("test_notification_receipts","notification_outbox","listing_matches","profile_listings",
                "monitoring_runs","requested_checks","profile_monitoring","listings","search_profiles",
                "telegram_replies","telegram_updates","app_settings"))jdbc.update("DELETE FROM "+table);
        org.mockito.Mockito.reset(api);
    }
    @Test void pairingRejectsUnknownUsersAndCommandsAreIdempotent() throws Exception {
        var pairing=connection.configure("123456:abcdefghijklmnopqrstuvwxyz0123456789");
        String start=pairing.get("command").toString();
        commands.accept(update(1,"900","/profiles"));
        assertEquals(0,count("telegram_replies"));
        commands.accept(update(2,"100",start));
        assertEquals("100",settings.get("telegram_owner",""));
        commands.accept(update(3,"900","/create {\"name\":\"bad\",\"criteria\":{},\"sources\":[\"AVITO\"]}"));
        assertEquals(0,count("search_profiles"));
        var create=update(4,"100","/create {\"name\":\"owner\",\"criteria\":{\"brand\":\"BMW\"},\"sources\":[\"AVITO\"]}");
        commands.accept(create);commands.accept(create);
        assertEquals(1,count("search_profiles"));
        assertEquals("5",settings.get("telegram_offset",""));
        var p=profiles.findAll().getFirst();
        commands.accept(update(5,"100","/edit "+p.id()+" {\"name\":\"edited\",\"criteria\":{},\"sources\":[\"AUTO_RU\"]}"));
        assertEquals("edited",profiles.findById(p.id()).name());
        commands.accept(update(6,"100","/check "+p.id()));
        assertEquals(1,count("requested_checks"));
        commands.accept(update(7,"100","/delete "+p.id()));
        assertEquals(0,count("search_profiles"));
        assertEquals(0,count("requested_checks"));
    }
    @Test void invalidCommandRollsBackAndCanBeRejectedWithoutBlockingOffset() throws Exception {
        connection.configure("123456:abcdefghijklmnopqrstuvwxyz0123456789");settings.set("telegram_owner","100");
        var update=update(50,"100","/delete "+UUID.randomUUID());
        assertThrows(RuntimeException.class,()->commands.accept(update));
        assertEquals(0,count("telegram_updates"));
        commands.reject(update);
        assertEquals("51",settings.get("telegram_offset",""));
        assertEquals(1,count("telegram_replies"));
    }
    @Test void pairingExpiresAndPrivateUserMustMatchChat() {
        var p=connection.configure("123456:abcdefghijklmnopqrstuvwxyz0123456789");
        String code=p.get("command").toString().substring(7);
        assertFalse(connection.bind(code,"100","101"));
        settings.set("telegram_pair_expires",Instant.EPOCH.toString());
        assertFalse(connection.bind(code,"100","100"));
        assertTrue(settings.publicSettings().keySet().stream().noneMatch(k->k.equals("telegram_token")));
    }
    @Test void deliveryFailureBacksOffThenSucceedsWithoutDuplicateAcknowledgement() {
        settings.set("telegram_token","123456:abcdefghijklmnopqrstuvwxyz0123456789");settings.set("telegram_owner","100");
        var profile=profiles.create(new CreateSearchProfileCommand("BMW",new SearchCriteria(null,null,null,null,null,null,null,null),Set.of(SourceType.AVITO)));
        var listing=new CarListing(SourceType.AVITO,"12345",URI.create("https://www.avito.ru/cars_12345"),
            "BMW",null,null,null,2000000L,null,null,Instant.now());
        ingestion.ingest(profile,List.of(listing));
        assertEquals("TELEGRAM",jdbc.queryForObject("SELECT channel FROM notification_outbox",String.class));
        doThrow(new TelegramApi.Failure("TELEGRAM_429",60)).when(api).send(anyString(),anyString());
        assertEquals(0,dispatcher.dispatch());
        assertEquals(1,jdbc.queryForObject("SELECT attempts FROM notification_outbox",Integer.class));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM notification_outbox",String.class));
        assertEquals(0,dispatcher.dispatch());verify(api,times(1)).send(anyString(),anyString());
        jdbc.update("UPDATE notification_outbox SET next_attempt_at=?",Instant.EPOCH.toString());
        doNothing().when(api).send(anyString(),anyString());
        assertEquals(1,dispatcher.dispatch());
        assertEquals(0,dispatcher.dispatch());
        assertEquals("DELIVERED",jdbc.queryForObject("SELECT status FROM notification_outbox",String.class));
    }
    @Test void sourceConfigurationPersistsAndRejectsOtherHosts() {
        var p=profiles.create(new CreateSearchProfileCommand("x",new SearchCriteria(null,null,null,null,null,null,null,null),Set.of(SourceType.AVITO)));
        monitoring.save(p.id(),new ProfileMonitoringService.Configuration(300,true,"https://www.avito.ru/rossiya/avtomobili?p=1",null,null));
        assertTrue(monitoring.get(p.id()).scheduled());
        assertEquals(300,monitoring.get(p.id()).intervalSeconds());
        assertThrows(IllegalArgumentException.class,()->monitoring.save(p.id(),new ProfileMonitoringService.Configuration(30,true,null,null,null)));
        assertThrows(IllegalArgumentException.class,()->monitoring.save(p.id(),new ProfileMonitoringService.Configuration(300,true,"https://avito.ru.evil.test/x",null,null)));
        assertThrows(IllegalArgumentException.class,()->ProfileMonitoringService.validateUrl("https://avito.ru@evil.test/x",SourceType.AVITO));
    }
    private JsonNode update(long id,String owner,String text) throws Exception {
        return json.valueToTree(Map.of("update_id",id,"message",Map.of("chat",Map.of("id",owner,"type","private"),
            "from",Map.of("id",owner),"text",text)));
    }
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
}

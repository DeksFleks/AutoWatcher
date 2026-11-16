package dev.autowatcher.telegram;

import dev.autowatcher.settings.SettingsService;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramConnectionService {
    private final SettingsService settings;
    public TelegramConnectionService(SettingsService settings) { this.settings=settings; }

    @Transactional
    public Map<String,Object> configure(String token) {
        if(token==null || !token.strip().matches("[0-9]+:[A-Za-z0-9_-]{20,}"))
            throw new IllegalArgumentException("Некорректный токен Telegram-бота");
        settings.disconnectTelegram();
        settings.set("telegram_token",token.strip());
        settings.set("telegram_status","Ожидается привязка владельца");
        return pair();
    }
    @Transactional
    public Map<String,Object> pair() {
        if(settings.get("telegram_token").isEmpty())
            throw new IllegalArgumentException("Сначала сохрани токен бота");
        byte[] bytes=new byte[16]; new SecureRandom().nextBytes(bytes);
        String code=HexFormat.of().formatHex(bytes);
        String expires=Instant.now().plusSeconds(600).toString();
        settings.set("telegram_pair_code",code);
        settings.set("telegram_pair_expires",expires);
        return Map.of("command","/start "+code,"expiresAt",expires);
    }
    @Transactional
    public boolean bind(String code,String chatId,String userId) {
        if(!chatId.equals(userId) || settings.get("telegram_owner").isPresent()) return false;
        String expected=settings.get("telegram_pair_code","");
        Instant expires=Instant.parse(settings.get("telegram_pair_expires",Instant.EPOCH.toString()));
        if(expected.isEmpty() || expires.isBefore(Instant.now()) || !MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                code.getBytes(java.nio.charset.StandardCharsets.UTF_8))) return false;
        settings.set("telegram_owner",chatId);
        settings.set("telegram_pair_code",null);
        settings.set("telegram_pair_expires",null);
        settings.set("telegram_status","Владелец привязан");
        return true;
    }
}

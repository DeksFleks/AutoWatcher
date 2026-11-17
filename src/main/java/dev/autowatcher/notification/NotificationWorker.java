package dev.autowatcher.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.background.enabled",havingValue="true",matchIfMissing=true)
public class NotificationWorker {
    private final TelegramOutboxDispatcher dispatcher;
    public NotificationWorker(TelegramOutboxDispatcher dispatcher) { this.dispatcher=dispatcher; }
    @Scheduled(fixedDelay=3000,initialDelay=5000)
    public void tick() { dispatcher.dispatch(); }
}

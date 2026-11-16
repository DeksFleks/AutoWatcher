package dev.autowatcher.desktop;

import java.awt.*;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DesktopLifecycle {
    private final ConfigurableApplicationContext context;
    private final boolean enabled;
    private final int port;
    public DesktopLifecycle(ConfigurableApplicationContext context,
            @Value("${app.desktop:false}") boolean enabled,@Value("${server.port:8080}") int port) {
        this.context=context;this.enabled=enabled;this.port=port;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        if(!enabled || GraphicsEnvironment.isHeadless()) return;
        EventQueue.invokeLater(()->{
            open();
            if(!SystemTray.isSupported()) return;
            var image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics=image.createGraphics();
            graphics.setColor(new Color(35,190,160));graphics.fillRoundRect(1,1,30,30,10,10);
            graphics.setColor(Color.BLACK);graphics.drawString("AW",6,21);graphics.dispose();
            PopupMenu menu=new PopupMenu();
            MenuItem launch=new MenuItem("Open AutoWatcher");launch.addActionListener(e->open());menu.add(launch);
            MenuItem exit=new MenuItem("Exit");exit.addActionListener(e->{
                new Thread(()->{context.close();System.exit(0);},"desktop-exit").start();
            });menu.add(exit);
            TrayIcon icon=new TrayIcon(image,"AutoWatcher",menu);icon.setImageAutoSize(true);
            icon.addActionListener(e->open());
            try { SystemTray.getSystemTray().add(icon); }
            catch(AWTException ignored) { }
        });
    }
    private void open() {
        try { if(Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create("http://127.0.0.1:"+port)); }
        catch(Exception ignored) { }
    }
}

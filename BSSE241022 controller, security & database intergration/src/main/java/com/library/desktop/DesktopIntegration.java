package com.library.desktop;

import com.library.hardware.ReaderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Desktop mode (library.desktop.enabled=true): system-tray icon + app-style window.
 * Closing the window does not stop the server (so the reader keeps working); use the tray menu "Exit".
 */
@Component
@ConditionalOnProperty(name = "library.desktop.enabled", havingValue = "true")
public class DesktopIntegration implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(DesktopIntegration.class);

    private volatile TrayIcon trayIcon;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext ctx = event.getApplicationContext();
        int port = ctx.getEnvironment().getProperty("local.server.port", Integer.class, 8080);
        String url = "http://localhost:" + port + "/";

        if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
            try {
                installTray(ctx, url);
            } catch (Exception e) {
                log.warn("Could not create the tray icon: {}", e.getMessage());
            }
        } else {
            log.info("No system tray available; running without a tray icon.");
        }
        DesktopWindow.open(url);
        log.info("Smart Library is running at {}", url);
    }

    @EventListener
    public void onReaderStatus(ReaderStatusChangedEvent event) {
        TrayIcon t = trayIcon;
        if (t != null) {
            t.setToolTip("Smart Library - Reader: " + event.status().state()
                    + (event.status().port() != null ? " (" + event.status().port() + ")" : ""));
        }
    }

    private void installTray(ConfigurableApplicationContext ctx, String url) throws AWTException {
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open Smart Library");
        open.addActionListener(e -> DesktopWindow.open(url));
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            int code = SpringApplication.exit(ctx, () -> 0);
            System.exit(code);
        });
        menu.add(open);
        menu.addSeparator();
        menu.add(exit);

        TrayIcon icon = new TrayIcon(createIcon(), "Smart Library", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> DesktopWindow.open(url));      // double-click
        SystemTray.getSystemTray().add(icon);
        trayIcon = icon;
    }

    /** Simple book icon drawn in code, so no image file is needed. */
    private static Image createIcon() {
        int s = 64;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x1f4e79));
        g.fillRoundRect(2, 2, s - 4, s - 4, 16, 16);
        g.setColor(Color.WHITE);
        g.fillRoundRect(12, 14, 18, 36, 4, 4);
        g.fillRoundRect(34, 14, 18, 36, 4, 4);
        g.setColor(new Color(0x2a9d8f));
        g.fillRect(30, 14, 4, 36);
        g.dispose();
        return img;
    }
}

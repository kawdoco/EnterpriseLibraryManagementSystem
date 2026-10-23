package com.library;

import com.library.desktop.DesktopWindow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@SuppressWarnings("unused")
public class SmartLibraryApplication {

    public static void main(String[] args) {
        try {
            // headless(false) lets the desktop mode create a system-tray icon.
            new SpringApplicationBuilder(SmartLibraryApplication.class).headless(false).run(args);
        } catch (Exception e) {
            // Desktop mode: if the app is already running, just bring up its window instead of failing.
            Integer port = findPortInUse(e);
            if (port != null && Boolean.getBoolean("library.desktop.enabled")) {
                DesktopWindow.open("http://localhost:" + port + "/");
                return;
            }
            throw e;
        }
    }

    private static Integer findPortInUse(Throwable t) {
        while (t != null) {
            if (t instanceof PortInUseException p) return p.getPort();
            t = t.getCause();
        }
        return null;
    }
}

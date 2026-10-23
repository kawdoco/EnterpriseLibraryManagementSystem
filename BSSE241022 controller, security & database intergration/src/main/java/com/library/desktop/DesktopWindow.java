package com.library.desktop;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Opens the application in its own window without browser tabs or address bar, using Microsoft Edge or
 * Google Chrome in "app mode". Falls back to the default browser if neither is found.
 */
public final class DesktopWindow {

    private DesktopWindow() {}

    public static void open(String url) {
        Path profile = Paths.get(System.getProperty("user.home"), ".smart-library", "browser-profile");
        for (Path exe : candidates()) {
            if (Files.isRegularFile(exe)) {
                try {
                    new ProcessBuilder(exe.toString(), "--app=" + url, "--window-size=1366,860",
                            "--user-data-dir=" + profile, "--no-first-run", "--no-default-browser-check")
                            .start();
                    return;
                } catch (Exception e) {
                    System.err.println("Could not start " + exe + ": " + e.getMessage() + " (trying the next browser)");
                }
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            System.err.println("Open " + url + " in your browser (could not launch it automatically).");
        }
    }

    private static List<Path> candidates() {
        List<Path> list = new ArrayList<>();
        for (String envVar : new String[]{"PROGRAMFILES(X86)", "PROGRAMFILES", "LOCALAPPDATA"}) {
            String base = System.getenv(envVar);
            if (base == null) continue;
            list.add(Paths.get(base, "Microsoft", "Edge", "Application", "msedge.exe"));
            list.add(Paths.get(base, "Google", "Chrome", "Application", "chrome.exe"));
        }
        for (String p : new String[]{"/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}) {
            list.add(new File(p).toPath());
        }
        return list;
    }
}

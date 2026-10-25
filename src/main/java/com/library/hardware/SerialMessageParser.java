package com.library.hardware;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns one line from the Arduino into a typed message.
 *
 * Protocol (new firmware):   UID:04A1B2C3D4   HB   STATUS:READY   STATUS:ERROR
 * Legacy (the Adafruit example sketch):   UID Value:  0xE1 0xCE 0x59 0x68
 * Everything else (human-readable chatter) is ignored, never treated as a UID.
 */
public final class SerialMessageParser {

    public sealed interface Message permits Uid, Heartbeat, Status, Ignored {}

    public record Uid(String uid, boolean legacy) implements Message {}

    public record Heartbeat() implements Message {}

    public record Status(String code) implements Message {}

    public record Ignored(String line, String reason) implements Message {}

    private static final Pattern LEGACY_UID = Pattern.compile("^UID\\s*VALUE\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_TOKEN = Pattern.compile("^0[xX]([0-9A-Fa-f]{1,2})$");

    private SerialMessageParser() {}

    public static Message parse(String rawLine) {
        if (rawLine == null) return new Ignored("", "empty");
        String line = rawLine.trim();
        if (line.isEmpty()) return new Ignored("", "empty");

        String upper = line.toUpperCase(Locale.ROOT);

        if (upper.equals("HB")) return new Heartbeat();

        if (upper.startsWith("STATUS:")) {
            return new Status(upper.substring("STATUS:".length()).trim());
        }

        if (upper.startsWith("UID:")) {
            String uid = UidNormalizer.normalize(line.substring(4));
            return uid == null ? new Ignored(line, "invalid UID") : new Uid(uid, false);
        }

        Matcher legacy = LEGACY_UID.matcher(line);
        if (legacy.matches()) {
            StringBuilder hex = new StringBuilder();
            for (String token : legacy.group(1).trim().split("\\s+")) {
                Matcher t = HEX_TOKEN.matcher(token);
                if (!t.matches()) return new Ignored(line, "invalid legacy UID");
                String b = t.group(1);
                if (b.length() == 1) hex.append('0');       // the example sketch prints 0x4, not 0x04
                hex.append(b);
            }
            String uid = UidNormalizer.normalize(hex.toString());
            return uid == null ? new Ignored(line, "invalid legacy UID") : new Uid(uid, true);
        }

        return new Ignored(line, "unrecognised");
    }
}

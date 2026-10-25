package com.library.hardware;

import com.library.hardware.SerialMessageParser.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SerialMessageParserTest {

    private static String uidOf(String line) {
        Message m = SerialMessageParser.parse(line);
        assertInstanceOf(Uid.class, m, "expected a UID for: " + line);
        return ((Uid) m).uid();
    }

    @Test
    void parsesProtocolUid() {
        assertEquals("04A1B2C3D4", uidOf("UID:04A1B2C3D4"));
        assertEquals("04A1B2C3", uidOf("uid:04:a1:b2:c3"));
        assertEquals("E1CE5968", uidOf("UID:E1CE5968\r"));
    }

    @Test
    void parsesLegacyExampleSketchOutput() {
        assertEquals("E1CE5968", uidOf("UID Value:  0xE1 0xCE 0x59 0x68"));
        assertEquals("04AB5C35C22A81", uidOf("UID Value:  0x4 0xAB 0x5C 0x35 0xC2 0x2A 0x81"));
        assertTrue(((Uid) SerialMessageParser.parse("UID Value:  0x69 0xBB 0x88 0x20")).legacy());
    }

    @Test
    void parsesHeartbeatAndStatus() {
        assertInstanceOf(Heartbeat.class, SerialMessageParser.parse("HB"));
        assertEquals(new Status("READY"), SerialMessageParser.parse("STATUS:READY"));
        assertEquals(new Status("ERROR"), SerialMessageParser.parse("STATUS:ERROR"));
    }

    @Test
    void ignoresChatterAndMalformedInput() {
        for (String line : new String[]{"Found an NFC card!", "UID Length: 4 bytes", "Found PN532 module.",
                "UID:ZZZZ", "UID:04", "UID Value:  0xE1 0xZZ 0x59", "", "   ", null}) {
            assertInstanceOf(Ignored.class, SerialMessageParser.parse(line), "should ignore: " + line);
        }
    }
}

package com.library.hardware;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineAssemblerTest {

    private static List<String> feed(LineAssembler a, String chunk) {
        byte[] b = chunk.getBytes();
        return a.feed(b, b.length);
    }

    @Test
    void reassemblesLinesSplitAcrossChunks() {
        LineAssembler a = new LineAssembler(128);
        List<String> out = new ArrayList<>();
        for (String c : new String[]{"UI", "D:E1CE", "5968\r", "\nHB\r\nUID:69BB", "8820\n"}) out.addAll(feed(a, c));
        assertEquals(List.of("UID:E1CE5968", "HB", "UID:69BB8820"), out);
    }

    @Test
    void partialLineWaitsForNewline() {
        assertTrue(feed(new LineAssembler(128), "UID:04A1").isEmpty());
    }

    @Test
    void overlongGarbageIsDroppedAndNextLineSurvives() {
        LineAssembler a = new LineAssembler(32);
        assertEquals(List.of("UID:E1CE5968"), feed(a, "X".repeat(50) + "\nUID:E1CE5968\n"));
    }
}

package com.library.hardware;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Serial data arrives in arbitrary chunks. This collects bytes and returns only COMPLETE
 * newline-terminated lines; a partial line waits for the rest. Over-long garbage is discarded.
 */
public final class LineAssembler {

    private final int maxLength;
    private final StringBuilder current = new StringBuilder();
    private boolean overflowed = false;

    public LineAssembler(int maxLength) {
        this.maxLength = maxLength;
    }

    public List<String> feed(byte[] data, int length) {
        List<String> lines = new ArrayList<>();
        String text = new String(data, 0, length, StandardCharsets.ISO_8859_1);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                if (!overflowed) {
                    String line = current.toString().trim();
                    if (!line.isEmpty()) lines.add(line);
                }
                current.setLength(0);
                overflowed = false;
            } else if (c != '\r') {
                if (current.length() >= maxLength) {
                    overflowed = true;          // drop this line entirely
                    current.setLength(0);
                } else if (!overflowed) {
                    current.append(c);
                }
            }
        }
        return lines;
    }
}

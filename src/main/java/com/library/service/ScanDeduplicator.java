package com.library.service;

/**
 * Suppresses repeated sightings of the same UID while a card/tag stays on the reader.
 * The window is SLIDING: every sighting (even a suppressed one) extends it, so a card held
 * against the reader stays suppressed, and a new scan is accepted only after the reader has
 * stopped reporting that UID for longer than the window (i.e. the card was removed).
 */
public final class ScanDeduplicator {

    private final long windowMs;
    private String lastUid;
    private long lastSeenAtMs;

    public ScanDeduplicator(long windowMs) {
        this.windowMs = windowMs;
    }

    public synchronized boolean isDuplicate(String uid, long nowMs) {
        boolean duplicate = uid.equals(lastUid) && (nowMs - lastSeenAtMs) <= windowMs;
        lastUid = uid;
        lastSeenAtMs = nowMs;
        return duplicate;
    }
}

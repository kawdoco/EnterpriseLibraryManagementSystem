package com.library.hardware;

import java.time.Instant;

/** A validated, normalized UID that came from the reader. Carries no database objects. */
public record RfidScanEvent(String uid, Instant at) {
}

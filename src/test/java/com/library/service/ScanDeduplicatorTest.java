package com.library.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanDeduplicatorTest {

    @Test
    void repeatedSightingsWhileCardStaysAreSuppressed() {
        ScanDeduplicator d = new ScanDeduplicator(2000);
        assertFalse(d.isDuplicate("E1CE5968", 0));
        assertTrue(d.isDuplicate("E1CE5968", 1000));
        assertTrue(d.isDuplicate("E1CE5968", 2800));      // window slides with every sighting
    }

    @Test
    void sameCardAfterRemovalIsANewScan() {
        ScanDeduplicator d = new ScanDeduplicator(2000);
        d.isDuplicate("E1CE5968", 0);
        assertFalse(d.isDuplicate("E1CE5968", 5000));
    }

    @Test
    void differentUidIsAlwaysNew() {
        ScanDeduplicator d = new ScanDeduplicator(2000);
        d.isDuplicate("E1CE5968", 0);
        assertFalse(d.isDuplicate("69BB8820", 100));
        assertFalse(d.isDuplicate("E1CE5968", 200));      // A, B, A: the last A is a fresh scan
    }
}

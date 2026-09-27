package com.library.lms.service;

import com.library.lms.dto.ScanResult;

public interface LibraryService {

    /**
     * Processes a single RFID scan event. Depending on the current status of
     * the scanned book, this either starts a new loan (borrow) or closes an
     * existing one (return, with fine calculation).
     *
     * @param rfidTag the tag read from the scanner (or typed via HID emulation)
     * @param userId  the member performing the scan
     */
    ScanResult handleRfidScan(String rfidTag, Long userId);
}

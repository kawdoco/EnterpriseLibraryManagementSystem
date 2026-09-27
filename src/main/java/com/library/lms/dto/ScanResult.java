package com.library.lms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Outcome of a single RFID scan, used to drive the flash alert on the
 * dashboard (success/error banner, human-readable message, and which
 * workflow ran).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanResult {

    private boolean success;
    private String message;

    /**
     * "BORROW" or "RETURN", null if the scan failed validation.
     */
    private String action;

    private double fineAmount;
}

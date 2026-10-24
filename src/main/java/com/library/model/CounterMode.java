package com.library.model;

public enum CounterMode {
    /** Member selected + book scanned = borrow. No member + borrowed book scanned = return. */
    AUTO,
    /** A member card must be scanned first, then books are borrowed. */
    BORROW,
    /** Every book scan is a return; member card not needed. */
    RETURN
}

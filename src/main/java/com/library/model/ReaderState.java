package com.library.model;

/** Connection state of the USB serial RFID reader, shown on every page. */
public enum ReaderState {
    DISABLED,            // library.serial.enabled=false
    DISCONNECTED,        // no port found / cannot open / unplugged
    CONNECTING,          // port opened, waiting for the Arduino to boot
    NO_DATA,             // port open but nothing received (wrong sketch / baud rate / wiring)
    READER_ERROR,        // Arduino reports that the PN532 was not detected
    ONLINE               // receiving heartbeats or scans
}

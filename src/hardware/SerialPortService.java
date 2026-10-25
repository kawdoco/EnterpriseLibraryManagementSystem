package com.library.hardware;

import com.fazecast.jSerialComm.SerialPort;
import com.library.config.LibraryProperties;
import com.library.dto.PortInfo;
import com.library.dto.ReaderStatus;
import com.library.model.ReaderState;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the USB serial connection to the Arduino.
 *
 * Responsibilities: find/open the port, read newline-terminated messages on its own thread,
 * monitor heartbeats, reconnect after unplugging, close cleanly on shutdown.
 *
 * It NEVER touches the database or any HTTP object. Valid UIDs leave this class only as
 * {@link RfidScanEvent} Spring events; connection changes as {@link ReaderStatusChangedEvent}.
 */
@Component
public class SerialPortService {

    private static final Logger log = LoggerFactory.getLogger(SerialPortService.class);
    private static final String[] ARDUINO_HINTS =
            {"arduino", "ch340", "ch341", "cp210", "ftdi", "usb serial", "usb-serial", "wch", "uno"};

    private final LibraryProperties props;
    private final ApplicationEventPublisher publisher;

    private final Object lock = new Object();
    private ScheduledExecutorService scheduler;

    private SerialPort port;                  // guarded by lock
    private Thread readerThread;              // guarded by lock
    private volatile boolean stopping = false;
    private volatile boolean forceReconnect = false;
    private volatile String preferredPort;    // runtime override from the UI; blank = use config / auto

    private volatile ReaderState state = ReaderState.DISCONNECTED;
    private volatile String message = "Starting...";
    private volatile String activePortName = null;
    private volatile long connectedAtMs = 0;
    private volatile long lastMessageAtMs = 0;
    private volatile long lastHeartbeatAtMs = 0;
    private volatile long lastAttemptAtMs = 0;
    private volatile boolean readerReportedError = false;
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong ignored = new AtomicLong();

    public SerialPortService(LibraryProperties props, ApplicationEventPublisher publisher) {
        this.props = props;
        this.publisher = publisher;
    }

    // ------------------------------------------------------------------ lifecycle

    @PostConstruct
    void start() {
        if (!props.getSerial().isEnabled()) {
            update(ReaderState.DISABLED, "Serial integration is disabled (library.serial.enabled=false).");
            return;
        }
        update(ReaderState.DISCONNECTED, "Looking for the Arduino...");
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "serial-monitor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::safeTick, 300, 1000, TimeUnit.MILLISECONDS);
        log.info("Serial reader monitor started ({} baud).", props.getSerial().getBaudRate());
    }

    @PreDestroy
    void stop() {
        stopping = true;
        if (scheduler != null) scheduler.shutdownNow();
        synchronized (lock) {
            closeLocked();
        }
        log.info("Serial port closed.");
    }

    // ------------------------------------------------------------------ public API

    public ReaderStatus status() {
        return new ReaderStatus(props.getSerial().isEnabled(), state, activePortName, message,
                lastMessageAtMs == 0 ? null : Instant.ofEpochMilli(lastMessageAtMs),
                lastHeartbeatAtMs == 0 ? null : Instant.ofEpochMilli(lastHeartbeatAtMs),
                received.get(), ignored.get());
    }

    public List<PortInfo> listPorts() {
        List<PortInfo> result = new ArrayList<>();
        try {
            for (SerialPort p : SerialPort.getCommPorts()) {
                result.add(new PortInfo(p.getSystemPortName(), p.getPortDescription(),
                        Objects.equals(p.getSystemPortName(), activePortName)));
            }
        } catch (RuntimeException e) {
            log.warn("Could not list serial ports: {}", e.getMessage());
        }
        return result;
    }

    /** Blank = automatic. Takes effect immediately (reconnects). */
    public void selectPort(String name) {
        preferredPort = (name == null || name.isBlank()) ? null : name.trim();
        reconnect();
    }

    public void reconnect() {
        forceReconnect = true;
    }

    // ------------------------------------------------------------------ monitor loop

    private void safeTick() {
        try {
            tick();
        } catch (RuntimeException e) {
            log.error("Serial monitor error", e);
        }
    }

    private void tick() {
        if (stopping) return;
        long now = System.currentTimeMillis();

        synchronized (lock) {
            if (forceReconnect) {
                forceReconnect = false;
                closeLocked();
                lastAttemptAtMs = 0;
            }
            if (port != null && port.isOpen() && readerThread != null && readerThread.isAlive()) {
                evaluateFreshnessLocked(now);
                return;
            }
            if (port != null) {                      // dead connection: clean up and report
                closeLocked();
                update(ReaderState.DISCONNECTED, "Reader disconnected. Reconnecting...");
            }
            if (now - lastAttemptAtMs < props.getSerial().getReconnectDelayMs()) return;
            lastAttemptAtMs = now;
            tryOpenLocked(now);
        }
    }

    private void evaluateFreshnessLocked(long now) {
        long reference = Math.max(lastMessageAtMs, connectedAtMs);
        long silentMs = now - reference;
        long timeout = props.getSerial().getHeartbeatTimeoutMs();
        if (readerReportedError) return;             // keep showing the reader's own error
        // The old example sketch sends no heartbeat, so silence means nothing there:
        // only enforce the timeout once heartbeats have been seen (new firmware) or nothing arrived at all.
        if (lastMessageAtMs != 0 && lastHeartbeatAtMs == 0) return;
        if (silentMs > timeout) {
            if (lastMessageAtMs == 0) {
                update(ReaderState.NO_DATA, "Connected to " + activePortName + " but nothing was received. "
                        + "Check the sketch is uploaded, the baud rate is "
                        + props.getSerial().getBaudRate() + ", and the PN532 wiring.");
            } else {
                update(ReaderState.NO_DATA, "No data from the reader for " + (silentMs / 1000) + " s.");
            }
        } else if (lastMessageAtMs == 0) {
            update(ReaderState.CONNECTING, "Connected to " + activePortName + ", waiting for the reader...");
        }
    }

    private void tryOpenLocked(long now) {
        String name = resolvePortName();
        if (name == null) {
            update(ReaderState.DISCONNECTED, "No Arduino found. Connect it with the USB cable"
                    + " (or set the port manually).");
            return;
        }
        SerialPort candidate;
        try {
            candidate = SerialPort.getCommPort(name);
            candidate.setComPortParameters(props.getSerial().getBaudRate(), 8,
                    SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            candidate.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
        } catch (RuntimeException e) {
            update(ReaderState.DISCONNECTED, "Serial port " + name + " is not valid: " + e.getMessage());
            return;
        }
        if (!candidate.openPort()) {
            update(ReaderState.DISCONNECTED, "Cannot open " + name + ". Close the Arduino IDE Serial Monitor"
                    + " or any other program using it, then try again.");
            return;
        }
        port = candidate;
        activePortName = name;
        connectedAtMs = now;
        lastMessageAtMs = 0;
        lastHeartbeatAtMs = 0;
        readerReportedError = false;
        final SerialPort opened = candidate;
        readerThread = new Thread(() -> readLoop(opened), "serial-reader-" + name);
        readerThread.setDaemon(true);
        readerThread.start();
        update(ReaderState.CONNECTING, "Connected to " + name + ", waiting for the reader...");
        log.info("Opened serial port {}", name);
    }

    private String resolvePortName() {
        if (preferredPort != null) return preferredPort;
        String configured = props.getSerial().getPort();
        if (configured != null && !configured.isBlank()) return configured.trim();
        SerialPort[] ports;
        try {
            ports = SerialPort.getCommPorts();
        } catch (RuntimeException e) {
            log.warn("Cannot enumerate serial ports: {}", e.getMessage());
            return null;
        }
        for (SerialPort p : ports) {
            String text = (p.getDescriptivePortName() + " " + p.getPortDescription()).toLowerCase(Locale.ROOT);
            for (String hint : ARDUINO_HINTS) {
                if (text.contains(hint)) return p.getSystemPortName();
            }
        }
        return ports.length == 1 ? ports[0].getSystemPortName() : null;
    }

    private void closeLocked() {
        SerialPort p = port;
        port = null;
        readerThread = null;
        if (p != null) {
            try {
                p.closePort();
            } catch (RuntimeException e) {
                log.debug("Closing the serial port failed (it may already be gone): {}", e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ reader thread

    private void readLoop(SerialPort p) {
        byte[] buf = new byte[256];
        LineAssembler assembler = new LineAssembler(128);
        try {
            while (!stopping && p.isOpen()) {
                int n = p.readBytes(buf, buf.length);
                if (n < 0) break;                    // error / cable pulled
                if (n == 0) continue;                // read timeout, keep waiting
                for (String line : assembler.feed(buf, n)) {
                    handleLine(line);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Serial read loop ended: {}", e.getMessage());
        } finally {
            onPortLost(p);
        }
    }

    private void handleLine(String line) {
        long now = System.currentTimeMillis();
        lastMessageAtMs = now;
        received.incrementAndGet();
        SerialMessageParser.Message msg = SerialMessageParser.parse(line);

        if (msg instanceof SerialMessageParser.Uid u) {
            markOnline();
            log.info("Scan received: {}{}", u.uid(), u.legacy() ? " (legacy format)" : "");
            publisher.publishEvent(new RfidScanEvent(u.uid(), Instant.ofEpochMilli(now)));
        } else if (msg instanceof SerialMessageParser.Heartbeat) {
            lastHeartbeatAtMs = now;
            markOnline();
        } else if (msg instanceof SerialMessageParser.Status s) {
            if (s.code().startsWith("ERROR")) {
                readerReportedError = true;
                update(ReaderState.READER_ERROR, "The Arduino cannot find the PN532 module. "
                        + "Check the wiring (SDA=A4, SCL=A5, 5V, GND) and the I2C switch settings.");
            } else {
                markOnline();
            }
        } else if (msg instanceof SerialMessageParser.Ignored ig) {
            ignored.incrementAndGet();
            if (!"empty".equals(ig.reason())) {
                log.debug("Ignored serial line ({}): {}", ig.reason(), ig.line());
            }
            // Any text at all proves the Arduino is alive (e.g. the old example sketch's chatter).
            if (state == ReaderState.CONNECTING || state == ReaderState.NO_DATA) markOnline();
        }
    }

    private void markOnline() {
        readerReportedError = false;
        update(ReaderState.ONLINE, "Reader online on " + activePortName + ".");
    }

    private void onPortLost(SerialPort p) {
        synchronized (lock) {
            if (port == p) {
                closeLocked();
                if (!stopping) {
                    update(ReaderState.DISCONNECTED, "Reader disconnected. Reconnecting...");
                    lastAttemptAtMs = System.currentTimeMillis();
                }
            }
        }
    }

    // ------------------------------------------------------------------ state publishing

    private void update(ReaderState newState, String newMessage) {
        boolean changed = state != newState || !Objects.equals(message, newMessage);
        state = newState;
        message = newMessage;
        if (changed) {
            try {
                publisher.publishEvent(new ReaderStatusChangedEvent(status()));
            } catch (RuntimeException e) {
                log.debug("Status listener failed: {}", e.getMessage());
            }
        }
    }
}

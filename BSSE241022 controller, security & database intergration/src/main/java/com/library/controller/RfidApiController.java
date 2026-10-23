package com.library.controller;

import com.library.dto.CounterRequests.ModeRequest;
import com.library.dto.CounterRequests.PortRequest;
import com.library.dto.CounterRequests.UidRequest;
import com.library.dto.CounterSnapshot;
import com.library.dto.LastUidResponse;
import com.library.dto.PortInfo;
import com.library.dto.ReaderStatus;
import com.library.hardware.SerialPortService;
import com.library.service.RfidCounterService;
import com.library.service.RfidStreamService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Hardware / status API used by the browser JavaScript. The browser never talks to the Arduino:
 * the Java backend owns the serial port and exposes results here.
 *
 *   GET  /api/rfid/stream       live updates (Server-Sent Events)
 *   GET  /api/rfid/state        same snapshot, for polling fallback
 *   GET  /api/rfid/status       reader connection only (top-bar badge)
 *   GET  /api/rfid/ports        serial ports on this computer
 *   POST /api/rfid/port         choose a port ("" = auto-detect)
 *   POST /api/rfid/reconnect    reopen the serial port
 *   POST /api/rfid/mode         AUTO / BORROW / RETURN
 *   POST /api/rfid/clear-member forget the selected member
 *   POST /api/rfid/manual       feed a typed UID through the same workflow
 *   GET  /api/rfid/last-uid     latest scanned UID (capture buttons on the forms)
 */
@RestController
@RequestMapping("/api/rfid")
public class RfidApiController {

    private final RfidCounterService counter;
    private final RfidStreamService stream;
    private final SerialPortService serial;

    public RfidApiController(RfidCounterService counter, RfidStreamService stream, SerialPortService serial) {
        this.counter = counter;
        this.stream = stream;
        this.serial = serial;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "counter") String role) {
        SseEmitter emitter = stream.register("counter".equals(role));
        stream.sendTo(emitter, counter.snapshotForCounterPage());
        return emitter;
    }

    @GetMapping("/state")
    public CounterSnapshot state(@RequestParam(defaultValue = "counter") String role) {
        return "counter".equals(role) ? counter.snapshotForCounterPage() : counter.snapshot();
    }

    @GetMapping("/status")
    public ReaderStatus status() {
        return serial.status();
    }

    @GetMapping("/ports")
    public List<PortInfo> ports() {
        return serial.listPorts();
    }

    @PostMapping("/port")
    public ResponseEntity<Map<String, Object>> selectPort(@RequestBody PortRequest request) {
        serial.selectPort(request.port());
        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    @PostMapping("/reconnect")
    public ResponseEntity<Map<String, Object>> reconnect() {
        serial.reconnect();
        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    @PostMapping("/mode")
    public CounterSnapshot mode(@RequestBody ModeRequest request) {
        counter.setMode(request.mode());
        return counter.snapshot();
    }

    @PostMapping("/clear-member")
    public CounterSnapshot clearMember() {
        counter.clearMember();
        return counter.snapshot();
    }

    @PostMapping("/manual")
    public ResponseEntity<Map<String, Object>> manual(@RequestBody UidRequest request) {
        counter.submitManual(request.uid());          // result arrives through the live stream
        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    @GetMapping("/last-uid")
    public LastUidResponse lastUid(@RequestParam(required = false) Long since) {
        return counter.lastUid(since);
    }
}

package com.library.service;

import com.library.dto.CounterSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events hub: pushes counter snapshots to open browser pages.
 * The serial thread never touches HTTP objects; it only calls {@link #broadcast}.
 */
@Service
public class RfidStreamService {

    private static final Logger log = LoggerFactory.getLogger(RfidStreamService.class);

    private record Client(SseEmitter emitter, boolean counterPage) {}

    private final List<Client> clients = new CopyOnWriteArrayList<>();

    public SseEmitter register(boolean counterPage) {
        SseEmitter emitter = new SseEmitter(0L);          // no timeout; dead clients are dropped on failed send
        Client client = new Client(emitter, counterPage);
        clients.add(client);
        Runnable remove = () -> clients.remove(client);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());
        return emitter;
    }

    public void sendTo(SseEmitter emitter, CounterSnapshot snapshot) {
        try {
            emitter.send(SseEmitter.event().name("state").data(snapshot, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            clients.removeIf(c -> c.emitter() == emitter);
        }
    }

    public void broadcast(CounterSnapshot snapshot) {
        for (Client c : clients) {
            sendTo(c.emitter(), snapshot);
        }
    }

    /** True while at least one counter page is open: only then do scans trigger borrow/return. */
    public boolean hasCounterClient() {
        for (Client c : clients) {
            if (c.counterPage()) return true;
        }
        return false;
    }

    @Scheduled(fixedRate = 15000)
    void keepAlive() {
        for (Client c : clients) {
            try {
                c.emitter().send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException e) {
                clients.remove(c);
                log.debug("Dropped a closed live-update connection");
            }
        }
    }
}

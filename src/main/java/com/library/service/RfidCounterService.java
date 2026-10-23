package com.library.service;

import com.library.config.LibraryProperties;
import com.library.dto.*;
import com.library.entity.Book;
import com.library.entity.LibraryMember;
import com.library.exception.ErrorCode;
import com.library.exception.LibraryException;
import com.library.hardware.ReaderStatusChangedEvent;
import com.library.hardware.RfidScanEvent;
import com.library.hardware.SerialPortService;
import com.library.hardware.UidNormalizer;
import com.library.model.BookStatus;
import com.library.model.CounterMode;
import com.library.model.ScanAction;
import com.library.model.TransactionStatus;
import com.library.repository.BookRepository;
import com.library.repository.BorrowTransactionRepository;
import com.library.repository.LibraryMemberRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * The counter workflow: scan events in, borrow/return results out.
 *
 * Threading: scans are processed one at a time on a dedicated worker thread (order preserved,
 * the serial reader is never blocked by database work). Small shared state is guarded by {@code lock}.
 * Business rules and transactions live in BorrowService; this class only decides WHICH action a scan means.
 *
 * Safety: scans change data ONLY while a counter page is open. Otherwise the UID is just remembered
 * (so the "Scan card/tag" buttons on the member and book forms can capture it).
 */
@Service
public class RfidCounterService {

    private static final Logger log = LoggerFactory.getLogger(RfidCounterService.class);
    private static final String ACTOR = "RFID_COUNTER";
    private static final long COUNTER_POLL_GRACE_MS = 6000;

    private final LibraryProperties props;
    private final SerialPortService serial;
    private final RfidStreamService stream;
    private final LibraryMemberRepository memberRepo;
    private final BookRepository bookRepo;
    private final BorrowTransactionRepository txRepo;
    private final BorrowService borrowService;
    private final FineService fineService;
    private final ScanDeduplicator deduplicator;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rfid-counter-worker");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private CounterMode mode = CounterMode.AUTO;
    private Long memberId;
    private MemberInfo memberInfo;
    private long memberExpiresAtMs;
    private final Deque<RfidEvent> recent = new ArrayDeque<>();
    private RfidEvent lastEvent;
    private long seq;

    private volatile String lastUid;
    private volatile long lastUidAtMs;
    private volatile long lastCounterPollMs;

    public RfidCounterService(LibraryProperties props, SerialPortService serial, RfidStreamService stream,
                              LibraryMemberRepository memberRepo, BookRepository bookRepo,
                              BorrowTransactionRepository txRepo, BorrowService borrowService,
                              FineService fineService) {
        this.props = props;
        this.serial = serial;
        this.stream = stream;
        this.memberRepo = memberRepo;
        this.bookRepo = bookRepo;
        this.txRepo = txRepo;
        this.borrowService = borrowService;
        this.fineService = fineService;
        this.deduplicator = new ScanDeduplicator(props.getSerial().getDuplicateWindowMs());
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    // ================================================================= inputs

    /** Scan from the reader (serial thread). Returns immediately; work happens on the worker thread. */
    @EventListener
    public void onScan(RfidScanEvent event) {
        long now = System.currentTimeMillis();
        lastUid = event.uid();
        lastUidAtMs = now;
        boolean duplicate = deduplicator.isDuplicate(event.uid(), now);
        if (duplicate) {
            log.debug("Duplicate sighting of {} ignored", event.uid());
            return;
        }
        submit(event.uid(), "SERIAL");
    }

    @EventListener
    public void onReaderStatus(ReaderStatusChangedEvent event) {
        broadcast();
    }

    /** Typed-in UID from the counter page (testing, or reader unavailable). Not de-duplicated. */
    public void submitManual(String rawUid) {
        String uid = UidNormalizer.normalize(rawUid);
        if (uid == null) {
            throw new LibraryException(ErrorCode.INVALID_UID,
                    "Enter a valid hexadecimal UID (4-10 bytes), e.g. E1CE5968.");
        }
        lastUid = uid;
        lastUidAtMs = System.currentTimeMillis();
        submit(uid, "MANUAL");
    }

    private void submit(String uid, String source) {
        try {
            worker.execute(() -> process(uid, source));
        } catch (RejectedExecutionException e) {
            log.debug("Scan dropped: application is shutting down");
        }
    }

    // ================================================================= processing

    private void process(String uid, String source) {
        try {
            if (!isCounterActive()) {
                log.info("UID {} recorded (no counter page is open, so no borrow/return was performed)", uid);
                return;
            }
            expireMemberIfNeeded();

            Optional<LibraryMember> member = memberRepo.findByRfidCardUid(uid);
            RfidEvent event;
            if (member.isPresent()) {
                event = onMemberCard(uid, source, member.get());
            } else {
                Optional<Book> book = bookRepo.findByRfidTagUid(uid);
                event = book.isPresent() ? onBookTag(uid, source, book.get()) : unknownTag(uid, source);
            }
            commit(event);
        } catch (LibraryException e) {
            commit(error(uid, source, null, null, e.getMessage()));
        } catch (DataAccessException e) {
            log.error("Database error while processing scan {}", uid, e);
            commit(error(uid, source, null, null,
                    "The database could not be reached. Nothing was changed. Please try again."));
        } catch (RuntimeException e) {
            log.error("Unexpected error while processing scan {}", uid, e);
            commit(error(uid, source, null, null, "Unexpected error. Nothing was changed. Please try again."));
        }
    }

    private RfidEvent onMemberCard(String uid, String source, LibraryMember m) {
        MemberInfo info = memberInfo(m);
        synchronized (lock) {
            memberId = m.getId();
            memberInfo = info;
            memberExpiresAtMs = System.currentTimeMillis() + props.getCounter().getMemberTimeoutSeconds() * 1000L;
        }
        RfidEvent e = base(uid, source, "MEMBER");
        e.setAction(ScanAction.MEMBER_SELECTED);
        e.setMember(info);
        if (!m.isActive()) {
            e.setSuccess(false);
            e.setTitle("Member is inactive");
            e.setMessage(m.getFullName() + " is deactivated and cannot borrow books.");
        } else if (m.isBlacklisted()) {
            e.setSuccess(false);
            e.setTitle("Member is blacklisted");
            e.setMessage(m.getFullName() + " is blacklisted and cannot borrow. Returns are still accepted.");
        } else if (info.activeLoans() >= info.maxLoans()) {
            e.setSuccess(false);
            e.setTitle("Borrowing limit reached");
            e.setMessage(m.getFullName() + " already has " + info.activeLoans() + " active loans (limit "
                    + info.maxLoans() + ").");
        } else {
            e.setSuccess(true);
            e.setTitle("Member identified");
            e.setMessage(m.getFullName() + (currentMode() == CounterMode.RETURN
                    ? ". Return mode is active: scan books to return them."
                    : ". Now scan the book's RFID tag to borrow."));
        }
        return e;
    }

    private RfidEvent onBookTag(String uid, String source, Book book) {
        Long mid;
        CounterMode m;
        synchronized (lock) {
            mid = memberId;
            m = mode;
        }
        boolean wantReturn = switch (m) {
            case RETURN -> true;
            case BORROW -> false;
            case AUTO -> mid == null && book.getStatus() == BookStatus.BORROWED;
        };
        if (wantReturn) return doReturn(uid, source, book);

        if (mid == null) {
            RfidEvent e = base(uid, source, "BOOK");
            e.setAction(ScanAction.NEED_MEMBER);
            e.setSuccess(false);
            e.setBook(bookInfo(book, book.getStatus().name()));
            e.setTitle("Scan the member's card first");
            e.setMessage("\"" + book.getTitle() + "\" identified. Scan the member's NFC card, then scan this book again.");
            return e;
        }
        return doBorrow(uid, source, book, mid);
    }

    private RfidEvent doBorrow(String uid, String source, Book book, Long mid) {
        try {
            BorrowResult r = borrowService.borrow(mid, uid, ACTOR);
            MemberInfo refreshed = memberRepo.findById(mid).map(this::memberInfo).orElse(null);
            synchronized (lock) {
                memberInfo = refreshed;
                memberExpiresAtMs = System.currentTimeMillis() + props.getCounter().getMemberTimeoutSeconds() * 1000L;
            }
            RfidEvent e = base(uid, source, "BOOK");
            e.setAction(ScanAction.BORROWED);
            e.setSuccess(true);
            e.setTitle("Successfully Borrowed");
            e.setMember(refreshed);
            e.setBook(bookInfo(book, BookStatus.BORROWED.name()));
            e.setBorrowedAt(r.borrowedAt());
            e.setDueDate(r.dueDate());
            e.setMessage(r.memberName() + " borrowed \"" + r.bookTitle() + "\". Loans: "
                    + r.activeLoansNow() + " of " + r.maxLoans() + ".");
            return e;
        } catch (LibraryException ex) {
            String msg = ex.getMessage();
            if (ex.getCode() == ErrorCode.BOOK_ALREADY_BORROWED && currentMode() == CounterMode.AUTO) {
                msg += " To return it, press \"Clear member\" (or switch to Return mode) and scan it again.";
            }
            MemberInfo mi;
            synchronized (lock) { mi = memberInfo; }
            return error(uid, source, mi, bookInfo(book, book.getStatus().name()), msg);
        }
    }

    private RfidEvent doReturn(String uid, String source, Book book) {
        try {
            ReturnResult r = borrowService.returnBook(uid, ACTOR);
            MemberInfo borrower = memberRepo.findById(r.memberId()).map(this::memberInfo).orElse(null);
            RfidEvent e = base(uid, source, "BOOK");
            e.setAction(ScanAction.RETURNED);
            e.setSuccess(true);
            e.setTitle("Successfully Returned");
            e.setMember(borrower);
            e.setBook(bookInfo(book, BookStatus.AVAILABLE.name()));
            e.setBorrowedAt(r.borrowedAt());
            e.setDueDate(r.dueDate());
            e.setReturnedAt(r.returnedAt());
            e.setOverdueDays(r.overdueDays());
            e.setFineAmount(r.fineAmount());
            e.setOutstandingFines(r.memberOutstandingFines());
            e.setMemberBlacklisted(r.memberBlacklisted());
            StringBuilder msg = new StringBuilder("\"").append(r.bookTitle()).append("\" returned by ")
                    .append(r.memberName()).append(". ");
            if (r.overdueDays() > 0) {
                msg.append("Late by ").append(r.overdueDays()).append(" day(s). Fine: ")
                        .append(props.getCurrency()).append(' ').append(r.fineAmount()).append('.');
            } else {
                msg.append("Returned on time. No fine.");
            }
            if (r.newlyBlacklisted()) {
                msg.append(" The member has now been BLACKLISTED (outstanding fines exceed ")
                        .append(props.getCurrency()).append(' ').append(props.getBlacklistThreshold()).append(").");
            }
            e.setMessage(msg.toString());
            return e;
        } catch (LibraryException ex) {
            return error(uid, source, null, bookInfo(book, book.getStatus().name()), ex.getMessage());
        }
    }

    private RfidEvent unknownTag(String uid, String source) {
        RfidEvent e = base(uid, source, "UNKNOWN");
        e.setAction(ScanAction.UNKNOWN_UID);
        e.setSuccess(false);
        e.setTitle("Unknown tag");
        e.setMessage("UID " + uid + " is not registered to any member or book. Register it from the Members or "
                + "Books page using the \"Scan card/tag\" button.");
        return e;
    }

    private RfidEvent error(String uid, String source, MemberInfo m, BookInfo b, String message) {
        RfidEvent e = base(uid, source, b != null ? "BOOK" : (m != null ? "MEMBER" : "UNKNOWN"));
        e.setAction(ScanAction.ERROR);
        e.setSuccess(false);
        e.setTitle("Not completed");
        e.setMessage(message);
        e.setMember(m);
        e.setBook(b);
        return e;
    }

    private RfidEvent base(String uid, String source, String kind) {
        RfidEvent e = new RfidEvent();
        e.setUid(uid);
        e.setSource(source);
        e.setKind(kind);
        return e;
    }

    private void commit(RfidEvent event) {
        synchronized (lock) {
            event.setSeq(++seq);
            lastEvent = event;
            recent.addFirst(event);
            while (recent.size() > props.getCounter().getHistorySize()) recent.removeLast();
        }
        broadcast();
    }

    // ================================================================= state for the UI

    public CounterSnapshot snapshot() {
        synchronized (lock) {
            expireLocked(System.currentTimeMillis());
            Long remaining = null;
            if (memberId != null) {
                remaining = Math.max(0, (memberExpiresAtMs - System.currentTimeMillis()) / 1000);
            }
            return new CounterSnapshot(seq, serial.status(), mode, memberInfo, remaining,
                    lastEvent, new ArrayList<>(recent), Instant.now());
        }
    }

    /** Called by the polling fallback so the counter counts as "open" even without SSE. */
    public CounterSnapshot snapshotForCounterPage() {
        lastCounterPollMs = System.currentTimeMillis();
        return snapshot();
    }

    public void setMode(CounterMode newMode) {
        if (newMode == null) throw new LibraryException(ErrorCode.INVALID_STATE, "Mode is required.");
        synchronized (lock) {
            mode = newMode;
        }
        broadcast();
    }

    public void clearMember() {
        synchronized (lock) {
            memberId = null;
            memberInfo = null;
        }
        broadcast();
    }

    /** Most recent UID seen after {@code sinceMillis}, with what it identifies (for the capture buttons). */
    public LastUidResponse lastUid(Long sinceMillis) {
        Instant now = Instant.now();
        String uid = lastUid;
        long at = lastUidAtMs;
        if (uid == null || (sinceMillis != null && at <= sinceMillis)) {
            return new LastUidResponse(null, null, null, null, now);
        }
        String kind = "UNKNOWN";
        String label = null;
        try {
            Optional<LibraryMember> m = memberRepo.findByRfidCardUid(uid);
            if (m.isPresent()) {
                kind = "MEMBER";
                label = m.get().getFullName() + " (" + m.get().getMemberCode() + ")";
            } else {
                Optional<Book> b = bookRepo.findByRfidTagUid(uid);
                if (b.isPresent()) {
                    kind = "BOOK";
                    label = b.get().getTitle();
                }
            }
        } catch (DataAccessException e) {
            log.warn("Could not look up UID {}: {}", uid, e.getMessage());
        }
        return new LastUidResponse(uid, kind, label, Instant.ofEpochMilli(at), now);
    }

    @Scheduled(fixedDelay = 1000)
    void expiryTick() {
        boolean cleared;
        synchronized (lock) {
            cleared = expireLocked(System.currentTimeMillis());
        }
        if (cleared) broadcast();
    }

    // ================================================================= helpers

    private boolean isCounterActive() {
        return stream.hasCounterClient()
                || (System.currentTimeMillis() - lastCounterPollMs) < COUNTER_POLL_GRACE_MS;
    }

    private CounterMode currentMode() {
        synchronized (lock) {
            return mode;
        }
    }

    private void expireMemberIfNeeded() {
        synchronized (lock) {
            expireLocked(System.currentTimeMillis());
        }
    }

    private boolean expireLocked(long now) {
        if (memberId != null && now >= memberExpiresAtMs) {
            memberId = null;
            memberInfo = null;
            return true;
        }
        return false;
    }

    private void broadcast() {
        try {
            stream.broadcast(snapshot());
        } catch (RuntimeException e) {
            log.debug("Broadcast failed: {}", e.getMessage());
        }
    }

    private MemberInfo memberInfo(LibraryMember m) {
        long loans = txRepo.countByMemberIdAndStatus(m.getId(), TransactionStatus.BORROWED);
        return new MemberInfo(m.getId(), m.getMemberCode(), m.getFullName(), m.getEmail(), m.getPhone(),
                m.isActive(), m.isBlacklisted(), loans, props.getMaxActiveLoans(),
                fineService.outstandingForMember(m.getId()), m.getRfidCardUid());
    }

    private BookInfo bookInfo(Book b, String status) {
        return new BookInfo(b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(), b.getCategory(),
                status, b.isActive(), b.getRfidTagUid(), b.getBorrowCount());
    }
}

package com.library.service.impl;

import com.library.dto.BookForm;
import com.library.entity.Book;
import com.library.exception.ErrorCode;
import com.library.exception.LibraryException;
import com.library.hardware.UidNormalizer;
import com.library.model.AuditAction;
import com.library.model.BookStatus;
import com.library.repository.BookRepository;
import com.library.repository.LibraryMemberRepository;
import com.library.service.AuditService;
import com.library.service.BookService;
import com.library.service.IsbnUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepo;
    private final LibraryMemberRepository memberRepo;
    private final AuditService audit;

    public BookServiceImpl(BookRepository bookRepo, LibraryMemberRepository memberRepo, AuditService audit) {
        this.bookRepo = bookRepo;
        this.memberRepo = memberRepo;
        this.audit = audit;
    }

    @Override
    @Transactional
    public Book register(BookForm form, String actor) {
        validateIsbn(form.getIsbn());
        String uid = normUid(form.getRfidTagUid());
        ensureUidFree(uid, null);

        Book b = new Book();
        apply(b, form);
        b.setRfidTagUid(uid);
        b.setStatus(BookStatus.AVAILABLE);
        b.setBorrowCount(0);
        b = saveChecked(b);
        audit.record(AuditAction.BOOK_REGISTERED, actor, "Book", b.getId(), b.getIsbn() + " " + b.getTitle());
        if (uid != null) {
            audit.record(AuditAction.BOOK_TAG_ASSIGNED, actor, "Book", b.getId(), "Tag " + uid);
        }
        return b;
    }

    @Override
    @Transactional
    public Book update(Long id, BookForm form, String actor) {
        validateIsbn(form.getIsbn());
        Book b = bookRepo.findByIdForUpdate(id).orElseThrow(() -> LibraryException.notFound("Book"));
        String uid = normUid(form.getRfidTagUid());
        boolean uidChanged = (uid == null ? b.getRfidTagUid() != null : !uid.equals(b.getRfidTagUid()));
        if (uidChanged) {
            if (b.getStatus() == BookStatus.BORROWED) {
                throw new LibraryException(ErrorCode.INVALID_STATE,
                        "Cannot change the RFID tag while the book is borrowed; it could not be returned by scan.");
            }
            ensureUidFree(uid, id);
        }
        apply(b, form);
        if (uidChanged) b.setRfidTagUid(uid);
        b = saveChecked(b);
        audit.record(AuditAction.BOOK_UPDATED, actor, "Book", b.getId(), b.getTitle());
        if (uidChanged) {
            audit.record(AuditAction.BOOK_TAG_ASSIGNED, actor, "Book", b.getId(), "Tag " + uid);
        }
        return b;
    }

    @Override
    @Transactional
    public Book assignTag(Long id, String rawUid, String actor) {
        String uid = normUid(rawUid);
        if (uid == null) throw new LibraryException(ErrorCode.INVALID_UID, "A tag UID is required.");
        Book b = bookRepo.findByIdForUpdate(id).orElseThrow(() -> LibraryException.notFound("Book"));
        if (b.getStatus() == BookStatus.BORROWED) {
            throw new LibraryException(ErrorCode.INVALID_STATE,
                    "Cannot change the RFID tag while the book is borrowed.");
        }
        ensureUidFree(uid, id);
        b.setRfidTagUid(uid);
        b = saveChecked(b);
        audit.record(AuditAction.BOOK_TAG_ASSIGNED, actor, "Book", b.getId(), "Tag " + uid);
        return b;
    }

    @Override
    @Transactional
    public void deactivate(Long id, String actor) {
        Book b = bookRepo.findByIdForUpdate(id).orElseThrow(() -> LibraryException.notFound("Book"));
        if (b.getStatus() == BookStatus.BORROWED) {
            throw new LibraryException(ErrorCode.INVALID_STATE, "This book is currently borrowed and cannot be deactivated.");
        }
        b.setActive(false);
        bookRepo.save(b);
        audit.record(AuditAction.BOOK_DEACTIVATED, actor, "Book", id, b.getTitle());
    }

    @Override
    @Transactional
    public void activate(Long id, String actor) {
        Book b = get(id);
        b.setActive(true);
        bookRepo.save(b);
        audit.record(AuditAction.BOOK_ACTIVATED, actor, "Book", id, b.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public Book get(Long id) {
        return bookRepo.findById(id).orElseThrow(() -> LibraryException.notFound("Book"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Book> search(String keyword, Pageable pageable) {
        return bookRepo.search(keyword == null ? "" : keyword.trim(), pageable);
    }

    // ---------- helpers ----------

    private void validateIsbn(String isbn) {
        if (!IsbnUtils.isValid(isbn)) {
            throw new LibraryException(ErrorCode.INVALID_STATE,
                    "Invalid ISBN. Enter a valid ISBN-10 or ISBN-13 (hyphens are optional).");
        }
    }

    private void apply(Book b, BookForm f) {
        b.setIsbn(IsbnUtils.normalize(f.getIsbn()));
        b.setTitle(f.getTitle().trim());
        b.setAuthor(f.getAuthor().trim());
        b.setPublisher(blank(f.getPublisher()) ? null : f.getPublisher().trim());
        b.setCategory(blank(f.getCategory()) ? null : f.getCategory().trim());
        b.setPublicationYear(f.getPublicationYear());
    }

    private String normUid(String raw) {
        try {
            return UidNormalizer.normalizeOrNull(raw);
        } catch (IllegalArgumentException e) {
            throw new LibraryException(ErrorCode.INVALID_UID, e.getMessage());
        }
    }

    /** Each physical copy needs its own tag, and a UID can never be both a card and a tag. */
    private void ensureUidFree(String uid, Long selfId) {
        if (uid == null) return;
        boolean usedByBook = selfId == null
                ? bookRepo.existsByRfidTagUid(uid)
                : bookRepo.existsByRfidTagUidAndIdNot(uid, selfId);
        if (usedByBook) throw LibraryException.duplicate("This RFID tag is already assigned to another book copy.");
        if (memberRepo.existsByRfidCardUid(uid)) {
            throw LibraryException.duplicate("This UID is already registered as a member card.");
        }
    }

    private Book saveChecked(Book b) {
        try {
            return bookRepo.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            throw new LibraryException(ErrorCode.DUPLICATE, "This RFID tag is already in use.", e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

package com.library.service.impl;

import com.library.config.LibraryProperties;
import com.library.dto.BorrowResult;
import com.library.dto.ReturnResult;
import com.library.entity.Book;
import com.library.entity.BorrowTransaction;
import com.library.entity.LibraryMember;
import com.library.exception.ErrorCode;
import com.library.exception.LibraryException;
import com.library.hardware.UidNormalizer;
import com.library.model.AuditAction;
import com.library.model.BookStatus;
import com.library.model.TransactionStatus;
import com.library.repository.BookRepository;
import com.library.repository.BorrowTransactionRepository;
import com.library.repository.LibraryMemberRepository;
import com.library.service.AuditService;
import com.library.service.BorrowService;
import com.library.service.FineService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lock order is ALWAYS: book row -> (transaction row) -> member row.
 * A consistent order prevents deadlocks between concurrent borrow/return/payment calls.
 * Everything runs in one database transaction, so any exception rolls back every change.
 */
@Service
public class BorrowServiceImpl implements BorrowService {

    private final LibraryProperties props;
    private final BookRepository bookRepo;
    private final LibraryMemberRepository memberRepo;
    private final BorrowTransactionRepository txRepo;
    private final FineService fineService;
    private final AuditService audit;

    public BorrowServiceImpl(LibraryProperties props, BookRepository bookRepo,
                             LibraryMemberRepository memberRepo, BorrowTransactionRepository txRepo,
                             FineService fineService, AuditService audit) {
        this.props = props;
        this.bookRepo = bookRepo;
        this.memberRepo = memberRepo;
        this.txRepo = txRepo;
        this.fineService = fineService;
        this.audit = audit;
    }

    @Override
    @Transactional
    public BorrowResult borrow(Long memberId, String bookTagUid, String actor) {
        if (memberId == null) {
            throw new LibraryException(ErrorCode.INVALID_STATE, "Scan the member's card before scanning a book.");
        }
        String uid = requireUid(bookTagUid);

        Book book = bookRepo.findByRfidTagUidForUpdate(uid)
                .orElseThrow(() -> new LibraryException(ErrorCode.UNKNOWN_UID, "This tag is not registered to any book."));
        LibraryMember member = memberRepo.findByIdForUpdate(memberId)
                .orElseThrow(() -> LibraryException.notFound("Member"));

        if (!member.isActive()) {
            throw new LibraryException(ErrorCode.MEMBER_INACTIVE, member.getFullName() + " is deactivated.");
        }
        if (member.isBlacklisted()) {
            throw new LibraryException(ErrorCode.MEMBER_BLACKLISTED,
                    member.getFullName() + " is blacklisted and cannot borrow books.");
        }
        if (!book.isActive()) {
            throw new LibraryException(ErrorCode.BOOK_INACTIVE, "\"" + book.getTitle() + "\" is deactivated.");
        }
        if (book.getStatus() != BookStatus.AVAILABLE) {
            throw new LibraryException(ErrorCode.BOOK_ALREADY_BORROWED,
                    "\"" + book.getTitle() + "\" is already borrowed.");
        }
        long active = txRepo.countByMemberIdAndStatus(memberId, TransactionStatus.BORROWED);
        if (active >= props.getMaxActiveLoans()) {
            throw new LibraryException(ErrorCode.BORROW_LIMIT_REACHED,
                    member.getFullName() + " has reached the limit of " + props.getMaxActiveLoans() + " active loans.");
        }

        LocalDateTime now = LocalDateTime.now();
        BorrowTransaction tx = new BorrowTransaction();
        tx.setMember(member);
        tx.setBook(book);
        tx.setBorrowedAt(now);
        tx.setDueDate(now.toLocalDate().plusDays(props.getBorrowDays()));
        tx.setFineAmount(BigDecimal.ZERO);
        tx.setStatus(TransactionStatus.BORROWED);
        tx.setActiveBookId(book.getId());   // explicit; DB constraint uk_active_book_loan relies on it

        book.setStatus(BookStatus.BORROWED);
        book.setBorrowCount(book.getBorrowCount() + 1);
        bookRepo.save(book);

        try {
            txRepo.saveAndFlush(tx);
        } catch (DataIntegrityViolationException e) {
            // uk_active_book_loan fired: another loan for this copy exists.
            throw new LibraryException(ErrorCode.BOOK_ALREADY_BORROWED,
                    "\"" + book.getTitle() + "\" is already borrowed.", e);
        }

        audit.record(AuditAction.BOOK_BORROWED, actor, "BorrowTransaction", tx.getId(),
                member.getMemberCode() + " borrowed \"" + book.getTitle() + "\", due " + tx.getDueDate());

        return new BorrowResult(tx.getId(), member.getId(), member.getMemberCode(), member.getFullName(),
                book.getId(), book.getTitle(), book.getIsbn(), tx.getBorrowedAt(), tx.getDueDate(),
                active + 1, props.getMaxActiveLoans());
    }

    @Override
    @Transactional
    public ReturnResult returnBook(String bookTagUid, String actor) {
        String uid = requireUid(bookTagUid);

        Book book = bookRepo.findByRfidTagUidForUpdate(uid)
                .orElseThrow(() -> new LibraryException(ErrorCode.UNKNOWN_UID, "This tag is not registered to any book."));
        BorrowTransaction tx = txRepo.findActiveByBookIdForUpdate(book.getId())
                .orElseThrow(() -> new LibraryException(ErrorCode.BOOK_NOT_BORROWED,
                        "\"" + book.getTitle() + "\" is not currently borrowed."));
        LibraryMember member = memberRepo.findByIdForUpdate(tx.getMember().getId())
                .orElseThrow(() -> LibraryException.notFound("Member"));

        LocalDateTime now = LocalDateTime.now();
        long overdue = fineService.overdueDays(tx.getDueDate(), now.toLocalDate());
        BigDecimal fine = fineService.calculateFine(tx.getDueDate(), now.toLocalDate());

        tx.setReturnedAt(now);
        tx.setFineAmount(fine);
        tx.setStatus(TransactionStatus.RETURNED);
        tx.setActiveBookId(null);           // frees the book for its next loan
        txRepo.saveAndFlush(tx);

        book.setStatus(BookStatus.AVAILABLE);
        bookRepo.save(book);

        audit.record(AuditAction.BOOK_RETURNED, actor, "BorrowTransaction", tx.getId(),
                member.getMemberCode() + " returned \"" + book.getTitle() + "\"");
        boolean newlyBlacklisted = false;
        if (fine.signum() > 0) {
            audit.record(AuditAction.FINE_ASSESSED, actor, "BorrowTransaction", tx.getId(),
                    overdue + " day(s) x " + props.getFineRatePerDay() + " = " + fine + " " + props.getCurrency());
            newlyBlacklisted = fineService.reassessBlacklist(member, actor);
        }

        return new ReturnResult(tx.getId(), member.getId(), member.getMemberCode(), member.getFullName(),
                book.getId(), book.getTitle(), book.getIsbn(),
                tx.getBorrowedAt(), tx.getDueDate(), now, overdue, fine,
                fineService.outstandingForMember(member.getId()),
                member.isBlacklisted(), newlyBlacklisted);
    }

    private String requireUid(String raw) {
        String uid = UidNormalizer.normalize(raw);
        if (uid == null) {
            throw new LibraryException(ErrorCode.INVALID_UID, "The scanned UID is not valid.");
        }
        return uid;
    }
}

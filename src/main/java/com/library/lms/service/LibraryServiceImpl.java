package com.library.lms.service;

import com.library.lms.dto.ScanResult;
import com.library.lms.model.Book;
import com.library.lms.model.Transaction;
import com.library.lms.model.User;
import com.library.lms.repository.BookRepository;
import com.library.lms.repository.TransactionRepository;
import com.library.lms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Core business logic for the RFID-driven borrow/return workflow.
 *
 * Scanning a tag is a single, overloaded action from the librarian's point
 * of view: scan an AVAILABLE book -> it gets borrowed; scan a BORROWED book
 * -> it gets returned (and a fine is assessed if it's late).
 */
@Service
public class LibraryServiceImpl implements LibraryService {

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BORROWED = "BORROWED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RETURNED = "RETURNED";

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Value("${library.loan.duration-days:14}")
    private int loanDurationDays;

    @Value("${library.fine.daily-rate:50.0}")
    private double dailyFineRate;

    @Value("${library.fine.blacklist-threshold:500.0}")
    private double blacklistThreshold;

    public LibraryServiceImpl(BookRepository bookRepository,
                               UserRepository userRepository,
                               TransactionRepository transactionRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public ScanResult handleRfidScan(String rfidTag, Long userId) {

        if (rfidTag == null || rfidTag.isBlank()) {
            return new ScanResult(false, "No RFID tag was scanned.", null, 0.0);
        }
        if (userId == null) {
            return new ScanResult(false, "Please select a member before scanning.", null, 0.0);
        }

        Optional<Book> bookOpt = bookRepository.findByRfidTag(rfidTag.trim());
        if (bookOpt.isEmpty()) {
            return new ScanResult(false, "No book found for RFID tag '" + rfidTag + "'.", null, 0.0);
        }
        Book book = bookOpt.get();

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return new ScanResult(false, "Member not found (ID: " + userId + ").", null, 0.0);
        }
        User user = userOpt.get();

        if (STATUS_AVAILABLE.equalsIgnoreCase(book.getStatus())) {
            // Blacklisting only prevents NEW borrows. A blacklisted member must
            // still be able to return books they are already holding, otherwise
            // those books would be stuck as BORROWED forever with no way back in.
            if (user.isBlacklisted()) {
                return new ScanResult(false,
                        "Access denied: " + user.getName()
                                + " is currently blacklisted due to unpaid fines and cannot borrow books.",
                        null, 0.0);
            }
            return processBorrow(book, user);
        } else if (STATUS_BORROWED.equalsIgnoreCase(book.getStatus())) {
            return processReturn(book);
        }

        return new ScanResult(false,
                "Book '" + book.getTitle() + "' has an unrecognized status: " + book.getStatus(),
                null, 0.0);
    }

    private ScanResult processBorrow(Book book, User user) {
        LocalDateTime now = LocalDateTime.now();

        book.setStatus(STATUS_BORROWED);
        book.setBorrowCount(book.getBorrowCount() + 1);
        bookRepository.save(book);

        Transaction transaction = new Transaction();
        transaction.setBook(book);
        transaction.setUser(user);
        transaction.setBorrowDate(now);
        transaction.setDueDate(now.plusDays(loanDurationDays));
        transaction.setReturnDate(null);
        transaction.setFineAmount(0.0);
        transaction.setStatus(STATUS_ACTIVE);
        transactionRepository.save(transaction);

        String message = String.format(
                "'%s' has been borrowed by %s. Due back on %s.",
                book.getTitle(), user.getName(), transaction.getDueDate().toLocalDate());

        return new ScanResult(true, message, "BORROW", 0.0);
    }

    private ScanResult processReturn(Book book) {
        Optional<Transaction> activeTxOpt = transactionRepository.findByBookAndStatus(book, STATUS_ACTIVE);

        if (activeTxOpt.isEmpty()) {
            // Data-consistency guard: book says BORROWED but there's no open loan.
            book.setStatus(STATUS_AVAILABLE);
            bookRepository.save(book);
            return new ScanResult(false,
                    "No active loan record found for '" + book.getTitle()
                            + "'. Status has been reset to AVAILABLE.", null, 0.0);
        }

        Transaction transaction = activeTxOpt.get();
        LocalDateTime now = LocalDateTime.now();
        transaction.setReturnDate(now);

        double fine = calculateFine(transaction.getDueDate(), now);
        transaction.setFineAmount(fine);
        transaction.setStatus(STATUS_RETURNED);
        transactionRepository.save(transaction);

        book.setStatus(STATUS_AVAILABLE);
        bookRepository.save(book);

        User user = transaction.getUser();
        boolean justBlacklisted = false;
        if (fine > blacklistThreshold) {
            user.setBlacklisted(true);
            userRepository.save(user);
            justBlacklisted = true;
        }

        String message;
        if (fine > 0) {
            message = String.format(
                    "'%s' returned by %s. Late fine: LKR %.2f%s",
                    book.getTitle(), user.getName(), fine,
                    justBlacklisted ? " — member has been BLACKLISTED for exceeding the fine limit." : "");
        } else {
            message = String.format(
                    "'%s' returned by %s on time. No fine due.", book.getTitle(), user.getName());
        }

        return new ScanResult(true, message, "RETURN", fine);
    }

    /**
     * Days-late * daily rate, with any partial day counted as a full day late.
     */
    private double calculateFine(LocalDateTime dueDate, LocalDateTime returnDate) {
        if (!returnDate.isAfter(dueDate)) {
            return 0.0;
        }
        long minutesLate = Duration.between(dueDate, returnDate).toMinutes();
        long daysLate = (long) Math.ceil(minutesLate / (24.0 * 60.0));
        if (daysLate < 1) {
            daysLate = 1;
        }
        return daysLate * dailyFineRate;
    }
}

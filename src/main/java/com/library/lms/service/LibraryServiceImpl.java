package com.library.lms.service;

import com.library.lms.dto.ScanResult;
import com.library.lms.model.*;
import com.library.lms.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class LibraryServiceImpl implements LibraryService {

    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final FineRepository fineRepository;

    @Value("${library.loan.duration-days:14}")
    private int loanDurationDays;

    @Value("${library.fine.daily-rate:50.0}")
    private double dailyFineRate;

    @Value("${library.fine.blacklist-threshold:500.0}")
    private double blacklistThreshold;

    public LibraryServiceImpl(BookRepository bookRepository,
                               UserRepository userRepository,
                               TransactionRepository transactionRepository,
                               FineRepository fineRepository) {
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.fineRepository = fineRepository;
    }

    @Override
    @Transactional
    public ScanResult handleRfidScan(String rfidTag, Long userId) {

        if (rfidTag == null || rfidTag.isBlank()) {
            return new ScanResult(false, "No RFID tag was scanned.", null, 0.0);
        }

        // RFID එක අනුව අදාළ පොත සෙවීම
        Optional<Book> bookOpt = bookRepository.findByRfidTagId(rfidTag.trim());
        if (bookOpt.isEmpty()) {
            return new ScanResult(false, "No book found for RFID tag '" + rfidTag + "'.", null, 0.0);
        }
        Book book = bookOpt.get();

        // 1. පොත දැනට Borrow කර ඇත්නම් -> Return Process එක සිදු කරයි
        Optional<Transaction> activeTxOpt = transactionRepository.findByBookAndStatus(book, BorrowStatus.BORROWED);
        if (activeTxOpt.isPresent()) {
            return processReturn(book, activeTxOpt.get());
        }

        // 2. පොත Borrow කර නැත්නම් -> New Borrow Process එක සිදු කරයි (UserId අවශ්‍ය වේ)
        if (userId == null) {
            return new ScanResult(false, "Please select a member before issuing a new book.", null, 0.0);
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return new ScanResult(false, "Member not found (ID: " + userId + ").", null, 0.0);
        }
        User user = userOpt.get();

        if (user.isBlacklisted()) {
            return new ScanResult(false,
                    "Access denied: " + user.getName() + " is currently blacklisted and cannot borrow books.",
                    null, 0.0);
        }

        if (book.getAvailableCopies() <= 0) {
            return new ScanResult(false, "No available copies left for '" + book.getTitle() + "'.", null, 0.0);
        }

        return processBorrow(book, user);
    }

    private ScanResult processBorrow(Book book, User user) {
        LocalDate today = LocalDate.now();

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);

        Transaction transaction = new Transaction(user, book, today, today.plusDays(loanDurationDays));
        transactionRepository.save(transaction);

        String message = String.format("'%s' has been borrowed by %s. Due back on %s.",
                book.getTitle(), user.getName(), transaction.getDueDate());

        return new ScanResult(true, message, "BORROW", 0.0);
    }

    private ScanResult processReturn(Book book, Transaction transaction) {
        LocalDate today = LocalDate.now();
        transaction.setReturnDate(today);
        transaction.setStatus(BorrowStatus.RETURNED);
        transactionRepository.save(transaction);

        book.setAvailableCopies(book.getAvailableCopies() + 1);
        bookRepository.save(book);

        double fineAmount = calculateFine(transaction.getDueDate(), today);
        User user = transaction.getUser();
        boolean justBlacklisted = false;

        if (fineAmount > 0) {
            Fine fine = new Fine(transaction, fineAmount);
            fineRepository.save(fine);

            if (fineAmount >= blacklistThreshold) {
                user.setBlacklisted(true);
                userRepository.save(user);
                justBlacklisted = true;
            }
        }

        String message;
        if (fineAmount > 0) {
            message = String.format("'%s' returned by %s. Late fine: LKR %.2f%s",
                    book.getTitle(), user.getName(), fineAmount,
                    justBlacklisted ? " — member has been BLACKLISTED for high unpaid fine." : "");
        } else {
            message = String.format("'%s' returned by %s on time. No fine due.", book.getTitle(), user.getName());
        }

        return new ScanResult(true, message, "RETURN", fineAmount);
    }

    private double calculateFine(LocalDate dueDate, LocalDate returnDate) {
        if (returnDate.isAfter(dueDate)) {
            long daysLate = ChronoUnit.DAYS.between(dueDate, returnDate);
            return daysLate * dailyFineRate;
        }
        return 0.0;
    }
}
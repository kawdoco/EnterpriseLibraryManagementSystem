package com.library.lms.repository;

import com.library.lms.model.Book;
import com.library.lms.model.BorrowStatus;
import com.library.lms.model.Transaction;
import com.library.lms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByUserAndBookAndStatus(User user, Book book, BorrowStatus status);
    Optional<Transaction> findByBookAndStatus(Book book, BorrowStatus status);
    List<Transaction> findByUserAndStatus(User user, BorrowStatus status);
    List<Transaction> findByStatus(BorrowStatus status);
}
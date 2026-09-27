package com.library.lms.repository;

import com.library.lms.model.Book;
import com.library.lms.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Used to find the currently open loan for a book (status = "ACTIVE")
     * when processing a return scan.
     */
    Optional<Transaction> findByBookAndStatus(Book book, String status);

    List<Transaction> findByUserOrderByBorrowDateDesc(com.library.lms.model.User user);
}

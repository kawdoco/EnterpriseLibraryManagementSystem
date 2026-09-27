package com.library.lms.repository;

import com.library.lms.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    /**
     * Looks a book up by the unique RFID tag attached to its physical cover.
     * This is the primary lookup used by the scan workflow.
     */
    Optional<Book> findByRfidTag(String rfidTag);

    /**
     * Returns the 5 most-borrowed books, most popular first, for the
     * dashboard's analytics widget.
     */
    List<Book> findTop5ByOrderByBorrowCountDesc();
}
